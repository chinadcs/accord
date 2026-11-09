"""Run one instrumentation class at a time on an isolated Accord package.

Build/install app.accord.uivalidation and its test APK first. Never clears the personal app.
Device settings are restored in finally; results and screenshots stay under app/build.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time
import threading


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--classes', nargs='+', required=True)
    parser.add_argument('--font', choices=['1', '2'], default='1')
    parser.add_argument('--rotation', choices=['0', '1'], default='0')
    parser.add_argument('--theme', choices=['light', 'dark'], default='light')
    parser.add_argument('--reduced-motion', action='store_true')
    parser.add_argument('--perf', action='store_true')
    parser.add_argument('--memory', action='store_true')
    parser.add_argument('--timeout', type=int, default=300)
    parser.add_argument('--reuse-data', action='store_true')
    args = parser.parse_args()
    if args.reuse_data and (not args.perf or args.classes != ['app.accord.AggregationPerformanceTest']):
        parser.error('--reuse-data is restricted to the opt-in isolated aggregation fixture')
    if any(not re.fullmatch(r'app\.accord\.[A-Za-z0-9]+(?:#[A-Za-z0-9_]+)?', c) for c in args.classes):
        parser.error('Invalid instrumentation selector')
    output = Path(args.output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    adb = Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/platform-tools/adb.exe'
    package = 'app.accord.uivalidation'

    def run(*parts, timeout=40, check=True):
        r = subprocess.run([str(adb), '-s', args.serial, *parts], capture_output=True, timeout=timeout)
        text = (r.stdout + r.stderr).decode('utf-8', errors='replace')
        if check and r.returncode:
            raise RuntimeError(f'adb {parts[:3]} failed: {r.returncode}')
        return text.strip()

    saved = {}
    results = []
    policy = run('shell', 'wm', 'fixed-to-user-rotation')
    user_rotation = run('shell', 'wm', 'user-rotation')
    if not re.fullmatch(r'(free|lock)(?: [0-3])?', user_rotation):
        raise RuntimeError('Unknown window-manager rotation setting')
    night = run('shell', 'cmd', 'uimode', 'night')
    night_mode = re.search(r'Night mode: (\w+)', night)
    if policy not in ('default', 'enabled', 'disabled') or not night_mode:
        raise RuntimeError('Unknown rotation/night setting; no changes made')
    for namespace, keys in [('system', ['font_scale', 'accelerometer_rotation', 'user_rotation', 'screen_off_timeout']),
                            ('global', ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'])]:
        for key in keys:
            saved[f'{namespace}/{key}'] = run('shell', 'settings', 'get', namespace, key)
    (output / 'settings-before.json').write_text(json.dumps({'settings': saved, 'rotationPolicy': policy, 'userRotation': user_rotation, 'night': night}, indent=2), encoding='utf-8')
    try:
        run('shell', 'wm', 'fixed-to-user-rotation', 'enabled')
        run('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
        run('shell', 'settings', 'put', 'system', 'screen_off_timeout', '1800000')
        run('shell', 'settings', 'put', 'system', 'font_scale', args.font)
        run('shell', 'settings', 'put', 'system', 'user_rotation', args.rotation)
        # One UI pode manter a rotação por display separada da chave Settings.System.
        run('shell', 'wm', 'user-rotation', 'lock', args.rotation)
        run('shell', 'cmd', 'uimode', 'night', 'yes' if args.theme == 'dark' else 'no')
        if args.reduced_motion:
            for key in ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']:
                run('shell', 'settings', 'put', 'global', key, '0')
        for selector in args.classes:
            folder = output / selector.removeprefix('app.accord.')
            folder.mkdir(exist_ok=True)
            run('shell', 'am', 'force-stop', package)
            if not args.reuse_data: run('shell', 'pm', 'clear', package)
            # Mudanças de tema/fonte da One UI podem reconfigurar o display após o primeiro lock.
            run('shell', 'wm', 'user-rotation', 'lock', args.rotation)
            expected_orientation = 'landscape' if args.rotation == '1' else 'portrait'
            for attempt in range(12):
                configuration = run('shell', 'cmd', 'activity', 'get-config')
                actual_orientation = 'landscape' if '-land' in configuration else 'portrait' if '-port' in configuration else 'unknown'
                if actual_orientation == expected_orientation: break
                run('shell', 'settings', 'put', 'system', 'user_rotation', args.rotation)
                run('shell', 'wm', 'user-rotation', 'lock', args.rotation)
                time.sleep(0.5)
            if actual_orientation != expected_orientation:
                raise RuntimeError('Display orientation did not change; refusing mislabeled run; ' + run('shell', 'wm', 'user-rotation'))
            start = time.monotonic()
            samples = []
            stop_sampling = threading.Event()
            def sample_memory():
                while not stop_sampling.is_set():
                    try:
                        raw = run('shell', 'dumpsys', 'meminfo', package, timeout=8, check=False)
                        sample = {name: int(match[1]) for name, pattern in [('pssKiB', r'TOTAL PSS:\s*(\d+)'), ('rssKiB', r'TOTAL RSS:\s*(\d+)')]
                                  if (match := re.search(pattern, raw))}
                        if sample:
                            sample['elapsedSeconds'] = round(time.monotonic() - start, 2)
                            samples.append(sample)
                    except (subprocess.TimeoutExpired, RuntimeError):
                        pass
                    stop_sampling.wait(1)
            sampler = threading.Thread(target=sample_memory, daemon=True) if args.memory else None
            if sampler: sampler.start()
            try:
                log = run('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', selector,
                          '-e', 'auditTheme', args.theme, '-e', 'auditRound', 's23-validation',
                          *(['-e', 'accord.perf', 'true'] if args.perf else []),
                          *(['-e', 'accord.perf.existing', 'true'] if args.reuse_data else []),
                          '-e', 'pilotCaptureAppOnly', 'true', f'{package}.test/app.accord.AccordTestRunner', timeout=args.timeout)
                (folder / 'instrumentation.txt').write_text(log, encoding='utf-8')
                match = re.search(r'OK \((\d+) tests?\)', log)
                codes = re.findall(r'INSTRUMENTATION_STATUS_CODE: (-?\d+)', log)
                skipped = codes.count('-3') + codes.count('-4')
                status = 'passed' if match and int(match[1]) > 0 and not skipped and not re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=', log) else 'failed-or-skipped'
                result = {'class': selector, 'status': status, 'tests': int(match[1]) if match else 0, 'skipped': skipped}
            except subprocess.TimeoutExpired as error:
                (folder / 'instrumentation.txt').write_text((error.stdout or b'').decode('utf-8', errors='replace'), encoding='utf-8')
                run('shell', 'am', 'force-stop', package)
                result = {'class': selector, 'status': 'timeout', 'tests': 0}
            finally:
                stop_sampling.set()
                if sampler: sampler.join(10)
                if args.memory:
                    (folder / 'memory-samples.json').write_text(json.dumps(samples, indent=2), encoding='utf-8')
            if samples:
                result['peakSampledPssKiB'] = max(s.get('pssKiB', 0) for s in samples)
                result['peakSampledRssKiB'] = max(s.get('rssKiB', 0) for s in samples)
            result.update(seconds=round(time.monotonic()-start, 2), font=args.font, theme=args.theme, rotation=args.rotation,
                          reducedMotion=args.reduced_motion, actualOrientation=actual_orientation)
            run('pull', f'/sdcard/Android/data/{package}/files', str(folder / 'external'), check=False)
            # Alguns testes guardam capturas no armazenamento interno isolado.
            for name in ['multicard-cards.png', 'multicard-invoice.png']:
                r = subprocess.run([str(adb), '-s', args.serial, 'exec-out', 'run-as', package, 'cat', f'files/{name}'], capture_output=True, timeout=20)
                if r.returncode == 0 and r.stdout.startswith(b'\x89PNG'):
                    (folder / name).write_bytes(r.stdout)
            result['screenshots'] = len(list(folder.rglob('*.png')))
            results.append(result)
            (output / 'results.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
            print(json.dumps(result), flush=True)
    finally:
        run('shell', 'am', 'force-stop', package, check=False)
        errors = []
        for full_key, value in saved.items():
            namespace, key = full_key.split('/')
            try:
                run('shell', 'settings', 'delete' if value == 'null' else 'put', namespace, key, *([] if value == 'null' else [value]))
            except Exception:
                errors.append(full_key)
        run('shell', 'wm', 'fixed-to-user-rotation', policy)
        run('shell', 'wm', 'user-rotation', *user_rotation.split())
        run('shell', 'cmd', 'uimode', 'night', night_mode[1])
        # Conferir após TODAS as restaurações: WM e One UI atualizam Settings de forma assíncrona.
        for full_key, value in saved.items():
            namespace, key = full_key.split('/')
            for attempt in range(12):
                if run('shell', 'settings', 'get', namespace, key) == value: break
                time.sleep(0.25)
            else: errors.append(full_key)
        restored_rotation = run('shell', 'wm', 'user-rotation')
        restored_policy = run('shell', 'wm', 'fixed-to-user-rotation')
        restored_night = run('shell', 'cmd', 'uimode', 'night')
        if restored_rotation != user_rotation: errors.append('wm/user-rotation')
        if restored_policy != policy: errors.append('wm/fixed-to-user-rotation')
        if restored_night != night: errors.append('uimode/night')
        (output / 'restoration.json').write_text(json.dumps({'errors': errors, 'rotationPolicy': restored_policy, 'userRotation': restored_rotation, 'night': restored_night}, indent=2), encoding='utf-8')
        if errors:
            raise RuntimeError('Settings not restored: ' + ', '.join(errors))
    if any(r['status'] != 'passed' for r in results):
        raise SystemExit(1)


if __name__ == '__main__':
    main()
