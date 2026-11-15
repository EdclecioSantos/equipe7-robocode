package equipe7;

import robocode.*;
import robocode.util.Utils;
import java.awt.Color;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * WOKEN - o ATIRADOR DE APOIO da Equipe 7.
 *
 * Papel no time:
 *  - Obedece o Deep: atira no mesmo alvo que ele (foco de fogo).
 *  - Fica longe (cerca de 450 px), no "vai e volta" de lado, dificil de acertar.
 *  - Atira com potencia media: a bala e mais rapida e acerta mais de longe.
 *  - Se o Deep morrer, passa a escolher o alvo sozinho.
 *
 * Heranca: Woken -> TeamRobot -> AdvancedRobot -> Robot.
 */
public class Woken extends TeamRobot {

    // ===== Configuracao do papel =====
    static final double DISTANCIA_IDEAL = 450;

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

    // Alvo que o Deep mandou
    String alvoDoLider = null;
    long alvoDoLiderQuando = -100;

    int sentido = -1;       // 1 ou -1: comeca no sentido contrario ao do Deep
    long ultimaVirada = 0;
    long proximaVirada = 20; // turno da proxima troca do "vai e volta"

    // =================================================================
    // Laco principal: a cada turno, escolhe alvo -> radar -> move -> atira
    // =================================================================
    public void run() {
        setColors(new Color(30, 90, 200), Color.BLACK, Color.WHITE);
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
            } else {
                setAhead(0);
            }
            enviar("EU|" + getX() + "|" + getY());   // avisa onde estou (evita fogo amigo)
            execute();
        }
    }

    // =================================================================
    // ALVO: o que o Deep mandou; se nao houver ordem, o mais perto
    // =================================================================
    void escolherAlvo() {
        Inimigo doLider = (alvoDoLider == null) ? null : inimigos.get(alvoDoLider);
        if (doLider != null && getTime() - alvoDoLiderQuando < 20 && getTime() - doLider.visto < 30) {
            alvo = alvoDoLider;
            return;
        }
        String melhor = null;
        double melhorNota = Double.MAX_VALUE, notaAtual = Double.MAX_VALUE;
        for (Inimigo i : inimigos.values()) {
            if (getTime() - i.visto > 30) continue;          // informacao velha demais
            double nota = distanciaAte(i.x, i.y);              // sem ordem do Deep: o mais perto
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
    // MOVIMENTO: longe e de lado, no "vai e volta" (muda de sentido sem ritmo fixo)
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

        // Vai e volta: troca de sentido em intervalos aleatorios de 15 a 45 turnos
        if (getTime() >= proximaVirada) virar();

        irPara(desviarParede(angulo));
    }

    // =================================================================
    // TIRO: mira circular + potencia de acordo com a distancia
    // =================================================================
    void atirar(Inimigo i) {
        double dist = distanciaAte(i.x, i.y);

        // Woken atira com potencia media: bala mais rapida, acerta mais de longe
        double potencia = dist < 250 ? 2.5 : 1.9;
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
        proximaVirada = getTime() + 15 + (long) (Math.random() * 30);
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
            // Energia do inimigo caiu entre 0.1 e 3 = ele acabou de atirar -> quase sempre mudamos de sentido
            double queda = i.energia - e.getEnergy();
            if (e.getName().equals(alvo) && queda >= 0.1 && queda <= 3 && getTime() - i.visto < 4
                    && getTime() - ultimaVirada > 6 && Math.random() < 0.8) {
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
        if (m[0].equals("ALVO")) {
            alvoDoLider = m[1];
            alvoDoLiderQuando = getTime();
        } else if (m[0].equals("EU")) {
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
