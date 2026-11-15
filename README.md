# Equipe 7 — Robocode

Time de dois tanques: **Deep** (líder, caçador) e **Woken** (atirador de apoio).
Os dois estendem `TeamRobot` (herança: `TeamRobot` → `AdvancedRobot` → `Robot`) e se comunicam por mensagens.

## Estrutura

```
equipe7/
    Deep.java
    Woken.java
    Equipe7.team
```

## Estratégia em uma frase

Os dois robôs atiram **no mesmo inimigo ao mesmo tempo** até ele morrer, e depois passam para o próximo: um de perto e forte, outro de longe e difícil de acertar.

## Papéis

| | **Deep — o caçador** | **Woken — o apoio** |
|---|---|---|
| Quem escolhe o alvo | Ele escolhe: o inimigo **mais fraco e mais perto** | Segue o alvo que o Deep mandar (escolhe sozinho só se o Deep morrer) |
| Distância | Perto, ~200 px | Longe, ~450 px |
| Movimento | Gira em volta do alvo, de lado para ele | "Vai e volta" de lado, trocando de sentido sem ritmo fixo |
| Tiro | Forte (potência 3,0 de perto, 2,5 média, 1,8 de longe) | Médio (2,5 de perto, 1,9 de longe): bala mais rápida |
| Ao ver o inimigo atirar | Troca de sentido em 50% das vezes | Troca de sentido em 80% das vezes |
| Energia inicial | **200** (é o líder do time) | 100 |

## Técnicas (iguais nos dois)

- **Mira circular:** simula o inimigo andando e girando como está agora, turno a turno, até a bala alcançá-lo, e mira nesse ponto.
- **Detecção de tiro:** quando a energia do inimigo cai entre 0,1 e 3, ele acabou de atirar, então mudamos de sentido para desviar.
- **Radar:** gira sem parar quando há vários inimigos e trava no alvo no 1 contra 1.
- **Potência econômica:** nunca gasta mais do que o necessário para matar (dano = 4×potência, mais 2×(potência−1) acima de 1) e economiza quando a vida está abaixo de 15.
- **Paredes:** se o caminho bate na parede, vai girando o ângulo até achar um caminho livre.
- **Trabalho em equipe:** trocam a posição de cada um e de todos os inimigos vistos (`INI`), o Deep manda o alvo (`ALVO`), e ninguém atira se o parceiro estiver na linha de tiro.

## Por que o Deep é o primeiro no `.team`

No Robocode, o **primeiro robô do time é o líder e começa com 200 de energia**. Se o líder morre, os parceiros perdem 30 de energia. Por isso o líder é o Deep, que vai para a linha de frente.
Nos testes, inverter a ordem (Woken como líder) derrubou a taxa de vitória contra o time mais forte de **59% para 15%**.

## Testes (Robocode 1.12.0, arena 800×600, melhor de 3, 2 contra 2)

| Adversário | Resultado |
|---|---|
| 6 times montados com robôs de exemplo (Walls, SpinBot, Crazy, Tracker, RamFire, TrackFire, VelociRobot, Corners) | 360 vitórias em 360 confrontos |
| Time "v4" com mira que aprende (GuessFactor) e desvio de balas (wave surfing), 745 linhas por robô | 271 vitórias em 460 confrontos (59%) |

## Como instalar

1. Copie a pasta `equipe7` para a pasta `robots` da instalação do Robocode.
2. No Robocode: **Robot → Source Editor**, abra `Deep.java` e `Woken.java` e compile os dois (**Compiler → Compile**).
3. Se preferir gerar o `.team` pelo Robocode: **Robot → Create a team**, adicione **primeiro o Deep** e depois o Woken.
4. **Battle → New** para testar.
