package equipe7;

import robocode.*;
import robocode.util.Utils;
import java.awt.Color;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * DEEP - o CACADOR (lider da Equipe 7).
 *
 * Papel no time:
 *  - Escolhe o alvo do time: o inimigo mais fraco e mais perto.
 *  - Avisa o Woken qual e o alvo, para os dois atirarem no mesmo inimigo (foco de fogo).
 *  - Chega perto (cerca de 200 px) e atira forte.
 *
 * Por ser o primeiro robo do arquivo .team, o Deep e o LIDER do time
 * e o Robocode da 200 de energia para ele (os outros comecam com 100).
 * Por isso ele e o que vai para a linha de frente.
 *
 * Heranca: Deep -> TeamRobot -> AdvancedRobot -> Robot.
 */
public class Deep extends TeamRobot {

    // ===== Configuracao do papel =====
    static final double DISTANCIA_IDEAL = 200;

    // ===== Ficha de um inimigo =====
    static class Inimigo {
        String nome;
        double x, y;        // posicao
        double direcao;     // para onde esta virado (radianos)
        double velocidade;
        double giro;        // quanto vira por turno (para a mira circular)
        double energia;
        long visto;         // turno em que foi visto pela ultima vez
    }

    Map<String, Inimigo> inimigos = new HashMap<String, Inimigo>();
    String alvo = null;

    // Posicao do parceiro (recebida por mensagem ou pelo radar)
    double parceiroX, parceiroY;
    long parceiroVisto = -100;
    int parceirosVivos;

    int sentido = 1;        // 1 ou -1: sentido em que orbitamos o alvo
    long ultimaVirada = 0;

    // =================================================================
    // Laco principal: a cada turno, escolhe alvo -> radar -> move -> atira
    // =================================================================
    public void run() {
        setColors(new Color(190, 30, 30), Color.BLACK, Color.WHITE);
        setAdjustGunForRobotTurn(true);     // canhao gira independente do corpo
        setAdjustRadarForGunTurn(true);     // radar gira independente do canhao
        setAdjustRadarForRobotTurn(true);
        parceirosVivos = (getTeammates() == null) ? 0 : getTeammates().length;

        while (true) {
            escolherAlvo();
            Inimigo i = (alvo == null) ? null : inimigos.get(alvo);

            controlarRadar(i);
            if (i != null) {
                mover(i);
                atirar(i);
                enviar("ALVO|" + alvo);          // avisa o Woken quem e o alvo
            } else {
                setAhead(0);
            }
            enviar("EU|" + getX() + "|" + getY());   // avisa onde estou (evita fogo amigo)
            execute();
        }
    }

    // =================================================================
    // ALVO: o inimigo mais fraco e mais perto
    // =================================================================
    void escolherAlvo() {
        String melhor = null;
        double melhorNota = Double.MAX_VALUE, notaAtual = Double.MAX_VALUE;
        for (Inimigo i : inimigos.values()) {
            if (getTime() - i.visto > 30) continue;          // informacao velha demais
            double nota = i.energia + distanciaAte(i.x, i.y) / 4;  // menor = melhor alvo
            if (i.nome.equals(alvo)) notaAtual = nota;
            if (nota < melhorNota) { melhorNota = nota; melhor = i.nome; }
        }
        // So troca de alvo se o novo for bem melhor (evita ficar trocando toda hora)
        if (alvo == null || notaAtual == Double.MAX_VALUE || melhorNota < notaAtual * 0.7) alvo = melhor;
    }

    // =================================================================
    // RADAR: gira sem parar com varios inimigos; trava no alvo no 1 contra 1
    // =================================================================
    void controlarRadar(Inimigo i) {
        int inimigosVivos = getOthers() - parceirosVivos;
        if (inimigosVivos == 1 && i != null && getTime() - i.visto < 3) {
            double giro = Utils.normalRelativeAngle(anguloAte(i.x, i.y) - getRadarHeadingRadians());
            setTurnRadarRightRadians(giro * 2);   // passa um pouco do alvo e volta: nunca perde
        } else {
            setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
        }
    }

    // =================================================================
    // MOVIMENTO: orbita o alvo perto, de lado para ele (dificil de acertar)
    // =================================================================
    void mover(Inimigo i) {
        double dist = distanciaAte(i.x, i.y);

        // De lado para o inimigo (90 graus), corrigindo para ficar na distancia ideal
        double correcao = limitar((dist - DISTANCIA_IDEAL) / 400, -0.5, 0.5);
        double angulo = anguloAte(i.x, i.y) + sentido * (Math.PI / 2 - correcao);

        // Afasta do parceiro se estiver muito perto
        if (getTime() - parceiroVisto < 10 && distanciaAte(parceiroX, parceiroY) < 150) {
            angulo = anguloAte(parceiroX, parceiroY) + Math.PI;
        }

        // Muda de sentido de vez em quando, para nao ficar previsivel
        if (getTime() - ultimaVirada > 40 && Math.random() < 0.03) virar();

        irPara(desviarParede(angulo));
    }

    // =================================================================
    // TIRO: mira circular + potencia de acordo com a distancia
    // =================================================================
    void atirar(Inimigo i) {
        double dist = distanciaAte(i.x, i.y);

        // Deep atira forte: quanto mais perto, mais forte
        double potencia = dist < 150 ? 3.0 : dist < 300 ? 2.5 : 1.8;
        potencia = ajustarPotencia(potencia, i);

        double[] p = preverPosicao(i, 20 - 3 * potencia);
        double angulo = anguloAte(p[0], p[1]);
        double erro = Utils.normalRelativeAngle(angulo - getGunHeadingRadians());
        setTurnGunRightRadians(erro);

        boolean mirado = Math.abs(erro) < Math.atan(18 / dist);
        if (getGunHeat() == 0 && mirado && getTime() - i.visto <= 4
                && !parceiroNaFrente(angulo, dist)) {
            setFire(potencia);
        }
    }

    // ===== A partir daqui: pecas iguais nos dois robos =====

    /** Nao gasta mais do que precisa para matar e economiza com pouca vida. */
    double ajustarPotencia(double potencia, Inimigo i) {
        // Dano de uma bala = 4 * potencia (+ 2 * (potencia - 1) se potencia > 1)
        double paraMatar = i.energia <= 4 ? i.energia / 4 : (i.energia + 2) / 6;
        potencia = Math.min(potencia, paraMatar + 0.1);
        if (getEnergy() < 15) potencia = Math.min(potencia, 1.0);
        return limitar(potencia, 0.1, Math.max(0.1, getEnergy() - 0.1));
    }

    /** Mira circular: simula o inimigo andando e girando como agora, ate a bala alcanca-lo. */
    double[] preverPosicao(Inimigo i, double velocidadeBala) {
        double x = i.x, y = i.y, dir = i.direcao;
        long atraso = getTime() - i.visto;      // quantos turnos a informacao tem
        for (int t = 1; t < 120; t++) {
            dir += i.giro;
            x = limitar(x + Math.sin(dir) * i.velocidade, 18, getBattleFieldWidth() - 18);
            y = limitar(y + Math.cos(dir) * i.velocidade, 18, getBattleFieldHeight() - 18);
            if (t > atraso && distanciaAte(x, y) <= velocidadeBala * (t - atraso)) break;
        }
        return new double[] { x, y };
    }

    /** Nao atira se o parceiro estiver no caminho da bala. */
    boolean parceiroNaFrente(double angulo, double distAlvo) {
        if (parceirosVivos == 0 || getTime() - parceiroVisto > 8) return false;
        double dx = parceiroX - getX(), dy = parceiroY - getY();
        double naLinha = dx * Math.sin(angulo) + dy * Math.cos(angulo);   // distancia ao longo do tiro
        double deLado = Math.abs(dx * Math.cos(angulo) - dy * Math.sin(angulo)); // distancia da linha
        return naLinha > 0 && naLinha < distAlvo + 30 && deLado < 50;
    }

    /** Se o caminho bate na parede, vai girando o angulo ate achar um caminho livre. */
    double desviarParede(double angulo) {
        for (int k = 0; k < 100; k++) {
            double x = getX() + Math.sin(angulo) * 120, y = getY() + Math.cos(angulo) * 120;
            if (x > 30 && y > 30 && x < getBattleFieldWidth() - 30 && y < getBattleFieldHeight() - 30) break;
            angulo += sentido * 0.06;
        }
        return angulo;
    }

    /** Anda na direcao do angulo, de frente ou de re (o que exigir menos giro). */
    void irPara(double angulo) {
        double giro = Utils.normalRelativeAngle(angulo - getHeadingRadians());
        int frente = 1;
        if (Math.abs(giro) > Math.PI / 2) {
            giro = Utils.normalRelativeAngle(giro + Math.PI);
            frente = -1;
        }
        setTurnRightRadians(giro);
        setMaxVelocity(Math.abs(giro) > 1 ? 4 : 8);   // freia em curva fechada
        setAhead(100 * frente);
    }

    void virar() {
        sentido = -sentido;
        ultimaVirada = getTime();
    }

    // =================================================================
    // EVENTOS
    // =================================================================
    public void onScannedRobot(ScannedRobotEvent e) {
        double ang = getHeadingRadians() + e.getBearingRadians();
        double x = getX() + Math.sin(ang) * e.getDistance();
        double y = getY() + Math.cos(ang) * e.getDistance();

        if (isTeammate(e.getName())) {
            parceiroX = x; parceiroY = y; parceiroVisto = getTime();
            return;
        }
        Inimigo i = inimigos.get(e.getName());
        if (i == null) {
            i = new Inimigo();
            i.nome = e.getName();
            inimigos.put(i.nome, i);
        } else {
            // Energia do inimigo caiu entre 0.1 e 3 = ele acabou de atirar -> as vezes mudamos de sentido
            double queda = i.energia - e.getEnergy();
            if (e.getName().equals(alvo) && queda >= 0.1 && queda <= 3 && getTime() - i.visto < 4
                    && getTime() - ultimaVirada > 10 && Math.random() < 0.5) {
                virar();
            }
        }
        atualizar(i, x, y, e.getHeadingRadians(), e.getVelocity(), e.getEnergy(), getTime());
        // Conta ao parceiro o que o radar viu
        enviar("INI|" + i.nome + "|" + x + "|" + y + "|" + e.getHeadingRadians() + "|"
                + e.getVelocity() + "|" + e.getEnergy() + "|" + getTime());
    }

    void atualizar(Inimigo i, double x, double y, double direcao, double vel, double energia, long t) {
        if (t <= i.visto) return;   // ja temos informacao mais nova
        long dt = t - i.visto;
        i.giro = (dt < 8) ? limitar(Utils.normalRelativeAngle(direcao - i.direcao) / dt, -0.17, 0.17) : 0;
        i.x = x; i.y = y; i.direcao = direcao; i.velocidade = vel; i.energia = energia; i.visto = t;
    }

    public void onMessageReceived(MessageEvent e) {
        if (!(e.getMessage() instanceof String)) return;
        String[] m = ((String) e.getMessage()).split("\\|");
        if (m[0].equals("EU")) {
            parceiroX = Double.parseDouble(m[1]);
            parceiroY = Double.parseDouble(m[2]);
            parceiroVisto = getTime();
        } else if (m[0].equals("INI")) {
            Inimigo i = inimigos.get(m[1]);
            if (i == null) { i = new Inimigo(); i.nome = m[1]; i.visto = -100; inimigos.put(m[1], i); }
            atualizar(i, Double.parseDouble(m[2]), Double.parseDouble(m[3]), Double.parseDouble(m[4]),
                    Double.parseDouble(m[5]), Double.parseDouble(m[6]), Long.parseLong(m[7]));
        }
    }

    public void onBulletHit(BulletHitEvent e) {
        Inimigo i = inimigos.get(e.getName());
        if (i != null) i.energia = e.getEnergy();   // para nao confundir com "ele atirou"
    }

    public void onHitWall(HitWallEvent e)   { virar(); }
    public void onHitRobot(HitRobotEvent e) { if (getTime() - ultimaVirada > 5) virar(); }

    public void onRobotDeath(RobotDeathEvent e) {
        if (isTeammate(e.getName())) parceirosVivos--;
        inimigos.remove(e.getName());
        if (e.getName().equals(alvo)) alvo = null;
    }

    // ===== Utilitarios =====
    void enviar(String msg) {
        if (parceirosVivos == 0) return;
        try { broadcastMessage(msg); } catch (IOException ignorado) { }
    }

    double anguloAte(double x, double y)    { return Math.atan2(x - getX(), y - getY()); }
    double distanciaAte(double x, double y) { return Math.hypot(x - getX(), y - getY()); }
    static double limitar(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
}
