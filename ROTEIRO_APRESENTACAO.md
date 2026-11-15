# Roteiro da apresentação — Equipe 7

Dois de nós são sorteados na hora. Este roteiro é para **qualquer um** conseguir apresentar. São uns 3 minutos.

## 1. A ideia (30 s)

"Muita gente vai encher o robô de funções. A gente fez o contrário: uma estratégia simples e certeira. **Os dois tanques atiram no mesmo inimigo ao mesmo tempo** até ele morrer. Fica 2 contra 1, e aí 2 contra 1 é muito difícil de perder."

## 2. Os dois papéis (1 min)

- **Deep (vermelho), o caçador:** escolhe o alvo do time, que é o inimigo mais fraco e mais perto. Fica perto, a uns 200 px, e atira forte.
- **Woken (azul), o apoio:** obedece o Deep e atira no mesmo alvo. Fica longe, a uns 450 px, fazendo um vai e volta de lado para ser difícil de acertar. Atira com potência média porque a bala sai mais rápida e acerta mais de longe.
- **Por que o Deep é o primeiro no .team:** o primeiro robô do time é o líder e o Robocode dá **200 de energia** para ele. Por isso ele vai na frente. Nos nossos testes, trocar a ordem fez a vitória cair de 59% para 15%.

## 3. Como funciona por dentro (1 min)

O `run()` repete a mesma coisa todo turno: **escolhe o alvo → radar → anda → atira**.

- **Herança e POO:** os dois estendem `TeamRobot`, que estende `AdvancedRobot`, que estende `Robot`. Sobrescrevemos os eventos `onScannedRobot`, `onMessageReceived`, `onHitWall`, etc. Cada inimigo vira um objeto `Inimigo` com posição, direção, velocidade e energia.
- **Mira circular (`preverPosicao`):** simulamos o inimigo andando e girando como agora, turno a turno, até a bala alcançá-lo. Atiramos nesse ponto.
- **Desvio:** a energia do inimigo caiu entre 0,1 e 3? Então ele atirou, e a gente troca de sentido.
- **Potência:** nunca gasta mais energia do que precisa para matar e economiza quando está com pouca vida.
- **Equipe:** mandamos mensagens `ALVO` (o Deep diz em quem atirar), `INI` (onde está cada inimigo) e `EU` (onde eu estou). Com a posição do parceiro, **ninguém atira se ele estiver na frente**.

## 4. Resultados (30 s)

Testamos em melhor de 3, 2 contra 2, igual ao torneio:
- Contra times de robôs de exemplo: 360 vitórias em 360.
- Contra um time bem mais complexo (com mira que aprende e desvio de balas, 745 linhas por robô): ganhamos 59%, com robôs de umas 290 linhas.

## Perguntas que o professor pode fazer

- **"Por que TeamRobot?"** Para os robôs trocarem mensagens e focarem o mesmo alvo.
- **"Como sabe que o inimigo atirou?"** Atirar gasta a energia da potência do tiro (0,1 a 3). Se a energia dele caiu nessa faixa, foi tiro.
- **"Por que o radar tem `setAdjust...`?"** Para o corpo, o canhão e o radar girarem de forma independente.
- **"Como evitam fogo amigo?"** Calculamos se o parceiro está perto da linha do tiro, entre nós e o alvo. Se estiver, não atiramos (`parceiroNaFrente`).
- **"O que acontece se o Deep morrer?"** O Woken perde 30 de energia (regra do líder) e passa a escolher o alvo sozinho: o inimigo mais perto.
