"""Galeria local de evidências. Notas vêm apenas dos TSVs de revisão humana/agente."""
import argparse
import csv
import hashlib
import html
import json
import os
import struct
from collections import Counter
from pathlib import Path


def build(root: Path, evidence: Path, output: Path) -> None:
    reviews = {}
    for file in sorted((root / 'docs/ui-audit-2026-11').glob('review-*.tsv')):
        with file.open(encoding='utf-8-sig', newline='') as stream:
            for row in csv.DictReader(stream, delimiter='\t'):
                reviews.setdefault(row['png'], []).append(dict(row, reviewer=file.stem))
    records = []
    reviewed_hashes = {}
    for meta_file in sorted(evidence.glob('*/emulator-*/*/rotation-*/font-*/*/*/run.json')):
        meta = json.loads(meta_file.read_text(encoding='utf-8-sig'))
        for image in sorted((meta_file.parent / 'screens').rglob('*.png')):
            path = image.relative_to(root).as_posix()
            data = image.read_bytes()
            digest = hashlib.sha256(data).hexdigest()
            width, height = struct.unpack('>II', data[16:24])
            own_reviews = reviews.get(path, [])
            if own_reviews:
                reviewed_hashes[digest] = (path, own_reviews)
            records.append(dict(path=path, image=image, sha256=digest, meta=meta, reviews=own_reviews,
                                width=width, height=height, orientation='paisagem' if width > height else 'retrato'))
    pairs = {}
    by_path = {record['path']: record for record in records}
    explicit_pairs = {}
    for path, path_reviews in reviews.items():
        for review in path_reviews:
            target = review.get('par_depois', '')
            if path in by_path and target in by_path and path != target:
                explicit_pairs[path] = target
                explicit_pairs.setdefault(target, path)
    for record in records:
        meta = record['meta']
        if meta['status'] == 'captured-not-reviewed':
            key = (meta['round'], meta['testClass'], str(meta['fontScale']), meta['theme'], str(meta['rotation']), record['image'].stem)
            pairs[key] = record
    cards = []
    inspected = 0
    for record in records:
        meta = record['meta']
        inherited = None
        if not record['reviews'] and record['sha256'] in reviewed_hashes:
            inherited, record['reviews'] = reviewed_hashes[record['sha256']]
        if record['reviews']:
            inspected += 1
        title = record['image'].stem
        context = f"{meta['round']} · {meta['theme']} · fonte {meta['fontScale']} · {record['orientation']} {record['width']}×{record['height']} · rotação solicitada {meta['rotation']}"
        captured = meta.get('started', '')
        rows = []
        for review in record['reviews']:
            # TSVs usam média ponderada em 0–100; a galeria apresenta em 0–10.
            score = float(review['nota_100']) / 10
            rows.append(f"<p><b>{score:.2f}/10 · {html.escape(review['decisao'])}</b> "
                        f"({html.escape(review['reviewer'])})<br>{html.escape(review['justificativa'])}</p>")
        if not rows:
            rows.append('<p class="pending">Não avaliado — sem nota atribuída.</p>')
        if (str(meta['rotation']) == '1') != (record['width'] > record['height']):
            rows.append('<p class="pending">Orientação real diverge da solicitada. Este PNG não comprova a orientação pretendida.</p>')
        if inherited:
            rows.append('<p>Mesmos bytes SHA-256 da imagem inspecionada: ' + html.escape(inherited) + '</p>')
        paired_round = 'after' if meta['round'] == 'before' else 'before'
        paired = by_path.get(explicit_pairs.get(record['path'], '')) or pairs.get(
            (paired_round, meta['testClass'], str(meta['fontScale']), meta['theme'], str(meta['rotation']), title),
        )
        if paired:
            paired_url = Path(os.path.relpath(paired['image'], output.parent)).as_posix()
            pair_meta = paired['meta']
            pair_context = f"{pair_meta['round']} · {pair_meta['theme']} · fonte {pair_meta['fontScale']} · {pair_meta.get('started', '')} · {pair_meta['status']}"
            rows.append('<details><summary>Comparar com a imagem relacionada</summary>'
                        f'<p>{html.escape(pair_context)}. A posição do conteúdo pode mudar com o layout; '
                        'a revisão explica os pares selecionados. Execuções incompletas continuam como diagnóstico.</p>'
                        f'<a href="{html.escape(paired_url, quote=True)}"><img loading="lazy" src="{html.escape(paired_url, quote=True)}" alt="Comparação de {html.escape(title, quote=True)}"></a></details>')
        image_url = Path(os.path.relpath(record['image'], output.parent)).as_posix()
        label = html.escape(title + ' ' + context + ' ' + ('avaliado' if record['reviews'] else 'pendente'), quote=True)
        cards.append(f'<article data-search="{label.lower()}"><h2>{html.escape(title)}</h2>'
                     f'<p>{html.escape(context)}<br>{html.escape(captured)}</p><a href="{html.escape(image_url, quote=True)}">'
                     f'<img loading="lazy" src="{html.escape(image_url, quote=True)}" alt="{label}"></a>'
                     + ''.join(rows) + f'<details><summary>Procedência</summary><p>{html.escape(record["path"])}</p>'
                     f'<p>Execução: {html.escape(meta["status"])}. SHA-256: {record["sha256"]}</p></details></article>')
    document = '''<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>Accord — auditoria visual</title><style>
body{font:16px system-ui,sans-serif;margin:24px;background:#f5f5f1;color:#20211e}h1{font-size:28px}h2{font-size:16px;overflow-wrap:anywhere}
input{font:inherit;padding:12px;width:min(90%,600px);margin-bottom:24px}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:24px}
article{background:white;padding:16px;border:1px solid #d7d7d1}img{width:100%;max-height:720px;object-fit:contain;background:#ecece7}
p{line-height:1.5;overflow-wrap:anywhere}.pending{color:#76530b}details{font-size:13px}article[hidden]{display:none}
</style><h1>Accord — evidências da auditoria visual</h1>'''
    document += f'<p>{len(records)} imagens; {inspected} com avaliação direta ou conteúdo byte a byte idêntico a uma imagem avaliada. '
    document += 'Captura não significa aprovação. Prints sintéticos não comprovam navegação real; notas visuais não comprovam TalkBack.</p>'
    document += '<p>Tema, fonte e rotação vêm do manifesto da execução. Sufixos históricos dos nomes de fixtures não definem a configuração real. Imagens anteriores à correção de contraste podem estar na rodada after; use a data e o par indicado pela revisão.</p>'
    counts = Counter((r['meta']['round'], r['meta']['theme'], str(r['meta']['fontScale']), r['orientation']) for r in records)
    document += '<details><summary>Distribuição das evidências</summary><table><tr><th>Rodada</th><th>Tema</th><th>Fonte</th><th>Orientação real</th><th>PNGs</th></tr>'
    document += ''.join('<tr>' + ''.join(f'<td>{html.escape(str(cell))}</td>' for cell in (*key, count)) + '</tr>' for key,count in sorted(counts.items()))
    document += '</table><p>Rotação0: retrato; rotação1: paisagem. Os PNGs de execuções incompletas são preservados como diagnóstico e não comprovam o restante da sequência.</p></details>'
    document += '<label>Filtrar por tela, tema, fonte ou rodada<br><input id="filter" type="search" placeholder="Ex.: categorias dark fonte 2"></label><main>'
    document += ''.join(cards) + '</main><script>document.querySelector("#filter").addEventListener("input",e=>{const terms=e.target.value.toLowerCase().split(/\\s+/);document.querySelectorAll("article").forEach(a=>a.hidden=!terms.every(t=>a.dataset.search.includes(t)))})</script></html>'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(document, encoding='utf-8')
    summary = dict(images=len(records), reviewed_or_identical=inspected, unreviewed=len(records)-inspected)
    output.with_suffix('.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
    print(json.dumps(summary))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--evidence', default='app/build/ui-audit')
    parser.add_argument('--output', default='app/build/ui-audit/gallery.html')
    args = parser.parse_args()
    build(Path.cwd(), Path(args.evidence).resolve(), Path(args.output).resolve())
