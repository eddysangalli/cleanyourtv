# Pendências — RSS cinematográfico (estado em 21/10/2026)

Publicado na `main` do fork (`46fccd0`, merge de `3836ab0`) com o build **2.5 / versionCode 6**
instalado e verificado na Philco 4K. Este arquivo registra **o que falta** para a próxima
sessão, para que nada se perca ao fechar o notebook.

APK publicado: `launcher/app/build/outputs/apk/release/app-release.apk`
SHA-256: `88E8E9CD7E5F556F434F4346E52190EABEF84FEE7DD9D41A06A8A616455A9D8E`
Baseline (rollback): `a3f37b5` (v2.4), APK salvo em
`%TEMP%\cleanyourtv-baseline-2.4-a3f37b5.apk`.

## Correções de código pendentes (prioridade)

1. **Encoding do feed da Folha (bug real).** O feed declara `encoding="ISO-8859-1"` mas serve
   bytes UTF-8 (ou charset errado no Content-Type); o resultado são `` em títulos e resumos.
   `NewsFeed.charsetOf(contentType)` usa só o Content-Type com default UTF-8. Correção
   sugerida: detectar bytes UTF-8 inválidos / declaração XML e redecodificar; testar com o feed
   real da Folha. G1, UOL, Sul21 e Leouve já saem corretos.
2. **Bug de foco na barra superior.** RIGHT a partir do pill "Entradas" (`btnInputs`) cai no
   card da grade em vez de seguir para o próximo pill. Todos os pills são `focusable=true` e o
   listener `pillKeys` só consome a tecla na ponta. Observado repetidamente via adb na Philco;
   provavel FocusFinder perdendo a mira com o rótulo que aparece/esconde (`lblInputs` +
   `animateLayoutChanges`). Verificar `nextFocusRight`/`showLabelOnFocus`.
3. **Artefato do glass/hero com RSS desligado.** Com a linha desligada, o destaque de foco
   (glass) aparece deslocado acima do card (posição errada ~`[112,273]` vs card em `[121,746]`)
   e o `heroName` fica grande. Ver `docs/assets/rss/home-rss-desligado.png`. Suspeita:
   `refitGlass` não reposiciona após `updateShelfLayout` mudar o padding do grid.
4. **Foco do popup do menu imprevisível.** Ao abrir o menu (long-press em Configurações), o
   item focado varia entre aberturas (por vezes item 1, por vezes item 2, por vezes nenhum);
   dificulta automação e confunde o usuário. Investigar `PopupMenu`/`showAsDropDown`.

## Testes e medições pendentes

5. **Benchmarks C–F** (ETAPA 7 incompleta): ocioso prolongado, abertura de apps com RSS ativo,
   regressão longa (horas), A-B-B-A. Os medidos (cenários A/B: jank, memória, first-frame)
   estão em `%TEMP%\bench_results.txt` — copiar para `docs/benchmarks/` antes de limpar o
   notebook.
6. **Testes físicos longos** na Philco: reboot com RSS ligado, standby/resume repetido,
   troca de Wi-Fi, TV ao vivo + Chromecast com a linha visível, retorno de Netflix/YouTube
   (sem replay de animação), 24 h de rotação ociosa. Nenhum ANR/crash observado até aqui.
7. **Toggle off→on com rede dura**: a correção do cache foi verificada uma vez (religou na
   hora); repetir o teste com Wi-Fi desligado para confirmar que a linha espera a rede sem
   quebrar o layout.
8. **`onTrimMemory` + RSS**: confirmar que o carrossel reexibe as fotos após trim (importa
   após o PR #585 do upstream).

## Ideias de forks adotáveis (documentadas, não implementadas)

- `PosterDiskCache` de 16 MB do finnwasabi (evita rebaixar pôsteres; custo barato).
- Busca sem acentos via AppSearch (joaomarigo; custo ≈0, testado em aparelhos de 1 GB).
- Prévia de wallpaper no foco (jichun29; ideia leve — sem vídeo Aerial/Bing).

## Itens do upstream aguardando medição própria

- #446 `hasOverlappingRendering=false` (on-hold; risco sobre gradientes do RSS).
- #523 `reduced_motion` (aberto; medir ≤5 frames/90 ms na Philco).
- #527 paletas, #535 HDMI dinâmico (`hide_inactive_hdmi`), #560 scancode (teste físico),
  #573 long-press, #595 overshoot (rejeitado por ora).
- #619/#621/#622 (ferramentas, `dirty`/`clean` upstream). Pilha Companion #618–#625:
  **não incorporar** (só proposta).

## Regra permanente

**O RSS não é enviado ao upstream** (`Gathin23/cleanyourtv`): o `CONTRIBUTING.md` exclui
fileiras novas. Só benchmarks, docs, correções genéricas e ferramentas podem virar PR de
upstream, depois de estabilizar (SHOWCASE).
