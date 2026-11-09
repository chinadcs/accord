"""Opt-in physical two-Galaxy smoke on a specified hosted project.

Requires explicit authorization, Supabase CLI administrative access and isolated test APKs.
Creates only synthetic users/spaces, keeps admin credentials on the PC, restores networking,
and removes its fixtures. Session tokens enter only the isolated app's internal sandbox.
"""
import argparse
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import urllib.error
import urllib.request
import uuid


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--project-ref', required=True)
    p.add_argument('--s20', required=True)
    p.add_argument('--s23', required=True)
    p.add_argument('--output', required=True)
    a = p.parse_args()
    if not re.fullmatch('[a-z]{20}', a.project_ref) or a.s20 == a.s23:
        p.error('Project and two distinct serials required')
    out = Path(a.output).resolve(); out.mkdir(parents=True, exist_ok=True)
    cli = os.environ.get('ACCORD_SUPABASE_CLI', 'supabase')
    keys = json.loads(subprocess.check_output([cli, 'projects', 'api-keys', '--project-ref', a.project_ref, '--reveal', '-o', 'json'], stderr=subprocess.PIPE))
    public = next(k['api_key'] for k in keys if k['name'] == 'anon')
    secret = next(k['api_key'] for k in keys if k['name'] == 'service_role')
    base = f'https://{a.project_ref}.supabase.co'
    adb = str(Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/platform-tools/adb.exe')
    package = 'app.accord.uivalidation'
    users, states, steps = [], {}, []
    space_id = str(uuid.uuid4())

    def http(path, data=None, token=None, admin=False, method='POST', allow_missing=False):
        key = secret if admin else public
        req = urllib.request.Request(base + path, data=None if data is None else json.dumps(data).encode(), method=method,
            headers={'apikey': key, 'Authorization': 'Bearer ' + (secret if admin else token or public), 'Content-Type': 'application/json'})
        try:
            with urllib.request.urlopen(req, timeout=35) as r:
                text = r.read()
                return json.loads(text) if text else None
        except urllib.error.HTTPError as e:
            if allow_missing and e.code == 404: return None
            raise RuntimeError(f'HTTP {e.code} at {path.split("?")[0]}') from None

    def device(serial, *parts, data=None, timeout=40, binary=False):
        r = subprocess.run([adb, '-s', serial, *parts], input=data, capture_output=True, timeout=timeout)
        if r.returncode:
            raise RuntimeError(f'adb {parts[:3]} failed on {serial}')
        return r.stdout if binary else r.stdout.decode('utf-8', errors='replace').strip()

    def network(serial, restore=False):
        state = states[serial]
        device(serial, 'shell', 'cmd', 'connectivity', 'airplane-mode', 'enable' if not restore or state['airplane'] == '1' else 'disable')
        for kind in ['wifi', 'data']:
            enabled = restore and state[kind] == '1'
            device(serial, 'shell', 'svc', kind, 'enable' if enabled else 'disable')
        if not restore:
            assert device(serial, 'shell', 'settings', 'get', 'global', 'airplane_mode_on') == '1'

    def phase(serial, name):
        log = device(serial, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class', 'app.accord.GalaxyHostedValidationTest',
            '-e', 'phase', name, f'{package}.test/app.accord.AccordTestRunner', timeout=240)
        label = 's23' if serial == a.s23 else 's20'
        (out / f'{len(steps):02d}-{label}-{name}.txt').write_text(log, encoding='utf-8')
        if not re.search(r'OK \(1 test\)', log) or re.search(r'FAILURES!!!|shortMsg=|INSTRUMENTATION_FAILED', log):
            raise RuntimeError(f'{label} phase {name} failed; see isolated log')
        result = json.loads(device(serial, 'exec-out', 'run-as', package, 'cat', 'files/galaxy-result.json'))
        result['device'] = label; steps.append(result)
        (out / 'results.json').write_text(json.dumps(steps, indent=2), encoding='utf-8')
        print(json.dumps(result), flush=True)
        # Morte de processo real entre fases; Room e fila precisam sobreviver.
        device(serial, 'shell', 'am', 'force-stop', package)

    try:
        for serial in [a.s23, a.s20]:
            states[serial] = {'airplane': device(serial, 'shell', 'settings', 'get', 'global', 'airplane_mode_on'),
                              'wifi': device(serial, 'shell', 'settings', 'get', 'global', 'wifi_on'),
                              'data': device(serial, 'shell', 'settings', 'get', 'global', 'mobile_data')}
            assert all(v in ['0', '1'] for v in states[serial].values()), 'Unknown network state'
        (out / 'network-before.json').write_text(json.dumps(states, indent=2), encoding='utf-8')
        for index, serial in enumerate([a.s23, a.s20]):
            email, password = f'galaxy-smoke-{uuid.uuid4()}@exemplo.test', secrets.token_hex(24)
            created = http('/auth/v1/admin/users', {'email': email, 'password': password, 'email_confirm': True}, admin=True)
            users.append(created['id'])
            signed = http('/auth/v1/token?grant_type=password', {'email': email, 'password': password})
            if index == 0:
                http('/rest/v1/rpc/create_space', {'p_space': space_id, 'p_name': 'Validação física fictícia'}, token=signed['access_token'])
                invite = http('/rest/v1/rpc/generate_invite', {'p_space': space_id}, token=signed['access_token'])
            else:
                http('/rest/v1/rpc/join_with_code', {'p_code': invite['code']}, token=signed['access_token'])
            cfg = {'url': base, 'publicKey': public, 'spaceId': space_id, 'accountId': created['id'], 'email': email,
                   'accessToken': signed['access_token'], 'refreshToken': signed['refresh_token'], 'device': 'S23' if index == 0 else 'S20'}
            device(serial, 'shell', 'am', 'force-stop', package)
            device(serial, 'shell', 'pm', 'clear', package)
            device(serial, 'shell', 'run-as', package, 'mkdir', '-p', 'files')
            # tee stdout (que contém a sessão) é capturado e descartado em memória.
            device(serial, 'exec-in', 'run-as', package, 'tee', 'files/galaxy-fixture.json', data=json.dumps(cfg).encode(), binary=True)
        phase(a.s23, 'seed')
        phase(a.s20, 'sync')
        for serial in [a.s23, a.s20]: network(serial)
        phase(a.s23, 'offline')
        phase(a.s20, 'offline')
        for serial in [a.s23, a.s20]: network(serial, restore=True)
        # A reconexão é validada pelo ciclo HTTP real, não só pelo estado das configurações.
        phase(a.s23, 'sync')
        phase(a.s20, 'conflicts')
        phase(a.s23, 'verify')
        phase(a.s20, 'verify')
        print('galaxy_hosted: duas instalações físicas convergiram', flush=True)
    finally:
        restore_errors = []
        restored = {}
        for serial in states:
            try:
                network(serial, restore=True)
                restored[serial] = {'airplane': device(serial, 'shell', 'settings', 'get', 'global', 'airplane_mode_on'),
                                    'wifi': device(serial, 'shell', 'settings', 'get', 'global', 'wifi_on'),
                                    'data': device(serial, 'shell', 'settings', 'get', 'global', 'mobile_data')}
                if restored[serial] != states[serial]: restore_errors.append(serial)
                device(serial, 'shell', 'am', 'force-stop', package)
                device(serial, 'shell', 'run-as', package, 'rm', '-f', 'files/galaxy-fixture.json')
            except Exception:
                restore_errors.append(serial)
        invites = http(f'/rest/v1/space_invites?space_id=eq.{space_id}&select=id', admin=True, method='GET')
        for item in invites:
            http(f'/rest/v1/space_invite_uses?invite_id=eq.{item["id"]}', admin=True, method='DELETE')
        for table in ['space_invites', 'applied_operations', 'space_entities', 'space_members']:
            http(f'/rest/v1/{table}?space_id=eq.{space_id}', admin=True, method='DELETE')
        http(f'/rest/v1/spaces?id=eq.{space_id}', admin=True, method='DELETE')
        for uid in users:
            for table in ['space_invite_uses', 'invite_attempts', 'privacy_space_notice']:
                http(f'/rest/v1/{table}?account_id=eq.{uid}', admin=True, method='DELETE')
            http(f'/auth/v1/admin/users/{uid}', admin=True, method='DELETE', allow_missing=True)
            assert http(f'/auth/v1/admin/users/{uid}', admin=True, method='GET', allow_missing=True) is None
        assert http(f'/rest/v1/spaces?id=eq.{space_id}&select=id', admin=True, method='GET') == []
        (out / 'cleanup.json').write_text(json.dumps({'remoteFixturesRemoved': True, 'networkRestoreErrors': restore_errors, 'networkAfter': restored}, indent=2), encoding='utf-8')
        print('galaxy_hosted: fixtures removidas; restauração de rede ' + ('FALHOU' if restore_errors else 'executada'), flush=True)
        if restore_errors: raise RuntimeError('Network restoration failed')


if __name__ == '__main__': main()
