package br.com.vagaviva.identity.application.port.in;

/** RF-03: garante que exista um ADMIN no primeiro start. */
public interface BootstrapAdminUseCase {

    /** @return {@code true} se o administrador inicial foi criado nesta chamada */
    boolean ensureAdminExists();
}
