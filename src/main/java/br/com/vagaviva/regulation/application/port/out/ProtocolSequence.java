package br.com.vagaviva.regulation.application.port.out;

/** Sequencial único do protocolo (sequence do banco — seguro com várias instâncias). */
public interface ProtocolSequence {

    long next();
}
