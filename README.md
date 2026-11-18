# Limpa Cache

App Android que limpa o cache de todos os apps com um toque, inspirado no 1Tap Cleaner.

## Como funciona

O Android não deixa um app comum apagar o cache dos outros. Por isso o Limpa Cache usa um "robô" de **Acessibilidade**: para cada app ele abre a tela "Informações do app", entra em "Armazenamento" e toca no botão "Limpar cache" sozinho — o mesmo truque do 1Tap Cleaner.

- **Acesso ao uso**: permite ver quanto cache cada app tem (lista ordenada do maior para o menor).
- **Acessibilidade**: liga o robô que toca em "Limpar cache". Nada é lido, guardado ou enviado.
- **Aparecer sobre outros apps** (recomendado): ajuda o robô a abrir as telas em segundo plano.
- **Botão rápido**: puxe a barra de notificações, edite os botões e adicione "Limpar cache".
- O cache do próprio Limpa Cache é apagado direto, sem robô.
- Sem anúncios, sem internet, sem coleta de dados.

## Gerar o APK no GitHub

1. Envie todos os arquivos deste projeto para um repositório seu.
2. Abra a aba **Actions** → **Gerar APK**. Ele roda sozinho a cada envio (ou toque em **Run workflow**).
3. Quando terminar (✓ verde), abra a execução e baixe **LimpaCache-APK** em *Artifacts*.
4. Descompacte o arquivo baixado e instale o `app-debug.apk`.

## Primeiro uso

1. Abra o app e toque em **Ativar** em cada passo (1, 2 e 3).
2. Toque em **LIMPAR CACHE AGORA** e espere. A tela vai piscar enquanto o robô passa por cada app.

## Observações

- Funciona em Android 8 ou mais novo. Os nomes dos botões são reconhecidos em português, inglês, espanhol e outros idiomas; em celulares com telas de Configurações muito diferentes (alguns modelos Xiaomi, Samsung etc.) algum app pode ser pulado.
- Ao instalar fora da Play Store, o Android 13+ pode bloquear a Acessibilidade: abra *Configurações → Apps → Limpa Cache → ⋮ → Permitir configurações restritas* e tente de novo.
