# Auditoria upstream e forks — outubro/2026

Escopo: o que foi analisado do upstream `eddysangalli/cleanyourtv` e de 6 forks, com decisão
por item. Regra desta branch: **nada do RSS é enviado ao upstream** (`CONTRIBUTING.md` do
upstream exclui features novas de fileira); só correções genéricas, benchmarks, docs e
ferramentas entram na fila de PR — e o RSS fica só no fork.

Data da auditoria: 21/10/2026. Upstream no commit `3e99673` (tag `v2.5.0`); nossa `main` no
baseline `a3f37b5` (v2.4). Merge-base `687b5ee`.

## PRs do upstream — lote 1 (desempenho e visuais)

| PR | Funcionalidade | Status upstream | Já existe aqui? | Aplicabilidade | Decisão | Evidência | Resultado |
|----|----------------|-----------------|-----------------|----------------|---------|-----------|-----------|
| #536 | Overdraw reduzido | merged (`65b8b9a`) | não | alta | adotar | −24% GPU medido upstream | aplicado via merge da main |
| #544 | Pills depois do primeiro frame | merged (`18a7841`) | não | alta | adotar | 52→43 ms no primeiro frame | aplicado |
| #552 | Retorno à Home sem replay de animação (flag `stopped`) | merged (`3db6cc6`) | não | alta | adotar | sem replay ao voltar de app | aplicado |
| #556 | Barra compacta opt-in | merged (`644cc34`) | não | média | adotar (opt-in) | flags `icon_only_buttons` etc. | aplicado; default inalterado |
| #569 | 12 anéis de foco finos | merged (`b73b36a`) | não | média | adotar | visual | aplicado |
| #446 | `hasOverlappingRendering=false` global | aberto, on-hold | não | incerta | não adotar sem medir | risco de arte em gradientes do RSS | pendente de medição própria |
| #523 | Movimento reduzido (`reduced_motion`) | aberto | não | média | adotar com medição | ≤5 frames/90 ms | aberto, aguarda benchmark |
| #527 | Paletas de wallpaper | aberto | não | média | adotar sem segundo sistema de temas | — | aberto |
| #535 | HDMI dinâmico via `TvInputManager` | aberto (conflita com main) | parcial (cards fixos) | alta | adotar com cuidado | `hide_inactive_hdmi` default off | aberto |
| #560 | Scancode de controles | aberto, needs-rework | não | média | só com teste físico | códigos TCL não importáveis | pendente |
| #573 | Menu long-press em apps | aberto, on-hold | parcial | média | não duplicar; nunca desinstalar em cards HDMI/RSS | nunca testado em TV | pendente |
| #595 | Overshoot de foco | aberto, needs-rework | não | baixa | não adotar | custo visual | rejeitado por ora |

## PRs do upstream — lote 2 (ferramentas, ambiente e utilidades)

| PR | Funcionalidade | Status upstream | Já existe aqui? | Aplicabilidade | Decisão | Resultado |
|----|----------------|-----------------|-----------------|----------------|---------|-----------|
| #434 | `tools/check.py` de drift | merged | não | alta (ferramenta) | adotar | aplicado |
| #567 | `b64push.py` (staging) | merged | não | alta | adotar | aplicado |
| #571 | `tv.py --screenshot` | merged | não | alta | adotar | aplicado (usado nas capturas) |
| #580 | Céu sunrise/sunset | merged | não | média | adotar (só sem RSS) | aplicado |
| #610 | Relógio ocioso (`ClockActivity`) | merged | não | média | adotar (só Home) | aplicado |
| #614 | Tema antes do storage montar | merged | não | alta | adotar | aplicado (só `Config.java`) |
| #616 | Cecotec + falso positivo `TV_INPUT_HARDWARE` | merged | não | média | adotar | aplicado |
| #570 | `wallpaper_true_color` (RGB_565 vs ARGB_8888) | merged | não | média | adotar medido | aplicado |
| #620 | Ordem de wallpapers (`wallpaper_order`) | merged | não | média | adotar (independente do RSS) | aplicado |
| #581 | Glow ambiente (`ambient_glow`) | merged | não | média | adotar com glow off sobre foto RSS | aplicado |
| #538 | `getChangedPackages` ao voltar à Home | merged | não | alta | adotar | aplicado (com #552) |
| #583 | Ícone sobre banner | merged | não | alta | adotar | aplicado |
| #584 | Consultas PackageManager | merged | não | alta | adotar | aplicado |
| #585 | Trim só com Home oculta | merged | não | alta | adotar; RSS reexibido após `onTrimMemory` | aplicado |
| #586 | Eventos de acessibilidade | merged | não | média | adotar | aplicado |
| #618/#623/#624/#625 | Pilha Companion (drafts) | abertos, drafts | não | baixa no launcher | **não incorporar**; documentado como proposta | não adotado |
| #619 | `handover --status` | aberto | não | ferramenta | adotar quando sair de `dirty` | pendente |
| #621 | Profiles (`profile.py`) | aberto | não | ferramenta | idem | pendente |
| #622 | Assinatura release (`release_manifest.py`) | aberto, clean | não | ferramenta | adotar | pendente |

## Forks estudados (código real, não descrição)

| Fork | Licença | Ideia | Já existe aqui? | Custo medido | Decisão |
|------|---------|-------|-----------------|--------------|---------|
| finnwasabi | MIT | Fileiras "Watch Next" + `PosterDiskCache` (16 MB) | não | +26 MB PSS medido na variante cheia; cache barato | **adotar a ideia** do cache de pôsteres; fileira completa só com medição própria |
| mclhm8182 | MIT | diversas melhorias visuais | sim, na maioria | — | não adotar (duplicação) |
| joaomarigo | MIT | AppSearch com títulos sem acentos (busca) | não | ≈0; testado em Philco/Caixun 1 GB | **adotar a ideia** |
| jichun29 | MIT | prévia de wallpaper no foco | não | leve | **adotar a ideia**; vídeo Aerial/Bing rejeitado (rede + decode) |
| serhatcamm | **sem LICENSE** | FileObserver para troca de wallpaper | não | — | **não copiar código**; apenas a ideia, reimplementada |
| reyhanfahlevi | MIT | servidor HTTP :8080 no `onCreate` | não | — | **não adotar** — superfície de rede sem autenticação |

## Regras que orientaram as decisões

1. Nada de RSS/fileiras novas para o upstream (CONTRIBUTING exclui) — o RSS vive só neste fork.
2. Nenhum fork vira base; só ideias pontuais, com licença MIT verificada no repositório.
3. Tudo que tem custo de memória/ GPU é medido na Philco antes de virar default.
4. `tools/` upstream é incorporado como está (não conflita com o launcher).
5. Itens on-hold/needs-rework do upstream só entram com medição e teste físico próprios.
