package br.com.vagaviva.patient.adapter.in.web;

import br.com.vagaviva.patient.adapter.in.web.PatientDtos.PatientResponse;
import br.com.vagaviva.patient.adapter.in.web.PatientDtos.RegisterPatientRequest;
import br.com.vagaviva.patient.adapter.in.web.PatientDtos.SearchPatientRequest;
import br.com.vagaviva.patient.adapter.in.web.PatientDtos.UpdateContactRequest;
import br.com.vagaviva.patient.application.port.in.ReadPatientUseCase;
import br.com.vagaviva.patient.application.port.in.ReadPatientUseCase.SearchPatientQuery;
import br.com.vagaviva.patient.application.port.in.RegisterPatientUseCase;
import br.com.vagaviva.patient.application.port.in.RegisterPatientUseCase.RegisterPatientCommand;
import br.com.vagaviva.patient.application.port.in.UpdatePatientContactUseCase;
import br.com.vagaviva.patient.application.port.in.UpdatePatientContactUseCase.UpdatePatientContactCommand;
import br.com.vagaviva.patient.domain.Patient;
import br.com.vagaviva.shared.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Pacientes", description = "Cadastro, busca e contato de pacientes (dados pessoais: CNS/CPF mascarados, acesso auditado)")
@RestController
@RequestMapping("/api/v1/patients")
@ApiResponse(responseCode = "401", description = "Sem token", content = @Content(mediaType = "application/problem+json"))
@ApiResponse(responseCode = "403", description = "Papel sem permissão", content = @Content(mediaType = "application/problem+json"))
class PatientController {

    private final RegisterPatientUseCase registerPatient;
    private final ReadPatientUseCase readPatient;
    private final UpdatePatientContactUseCase updateContact;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    PatientController(RegisterPatientUseCase registerPatient, ReadPatientUseCase readPatient,
            UpdatePatientContactUseCase updateContact, CurrentUserProvider currentUser, Clock clock) {
        this.registerPatient = registerPatient;
        this.readPatient = readPatient;
        this.updateContact = updateContact;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Operation(summary = "Cadastrar paciente (REQUESTER, ADMIN)",
            description = "CNS obrigatório com dígito verificador; CPF opcional. A resposta devolve CNS/CPF mascarados.")
    @ApiResponse(responseCode = "201", description = "Criado; header Location aponta para o recurso")
    @ApiResponse(responseCode = "409", description = "CNS ou CPF já cadastrado (CNS_ALREADY_REGISTERED, CPF_ALREADY_REGISTERED)", content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "422", description = "CNS, CPF, telefone ou município inválidos (INVALID_CNS, INVALID_CPF, INVALID_PHONE…)", content = @Content(mediaType = "application/problem+json"))
    @PostMapping
    @PreAuthorize("hasAnyRole('REQUESTER','ADMIN')")
    ResponseEntity<PatientResponse> register(@Valid @RequestBody RegisterPatientRequest request, HttpServletRequest http) {
        Patient patient = registerPatient.register(new RegisterPatientCommand(request.cns(), request.cpf(),
                request.fullName(), request.socialName(), request.birthDate(), request.municipalityCode(),
                request.phone(), request.preferredChannel(), Boolean.TRUE.equals(request.whatsappOptIn()),
                Boolean.TRUE.equals(request.pregnant()), Boolean.TRUE.equals(request.disability()), currentUser.get(),
                http.getRemoteAddr()));
        return ResponseEntity.created(URI.create("/api/v1/patients/" + patient.id()))
                .body(PatientResponse.from(patient, clock));
    }

    @Operation(summary = "Consultar paciente (REQUESTER, REGULATOR, ADMIN)", description = "Leitura auditada (PATIENT_READ).")
    @ApiResponse(responseCode = "200", description = "Paciente com CNS/CPF mascarados")
    @ApiResponse(responseCode = "404", description = "Inexistente (PATIENT_NOT_FOUND)", content = @Content(mediaType = "application/problem+json"))
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('REQUESTER','REGULATOR','ADMIN')")
    PatientResponse get(@PathVariable UUID id, HttpServletRequest http) {
        return PatientResponse.from(readPatient.get(id, currentUser.get(), http.getRemoteAddr()), clock);
    }

    @Operation(summary = "Buscar paciente por CNS ou CPF (REQUESTER, REGULATOR, ADMIN)",
            description = "Documento no corpo — dados pessoais nunca na URL (RN-22). Informe exatamente um critério. Busca auditada, inclusive sem resultado.")
    @ApiResponse(responseCode = "200", description = "Paciente encontrado")
    @ApiResponse(responseCode = "404", description = "Nenhum paciente com o documento (PATIENT_NOT_FOUND)", content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "422", description = "Nenhum ou os dois critérios (INVALID_SEARCH_CRITERIA), ou documento inválido", content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/search")
    @PreAuthorize("hasAnyRole('REQUESTER','REGULATOR','ADMIN')")
    PatientResponse search(@Valid @RequestBody SearchPatientRequest request, HttpServletRequest http) {
        return PatientResponse.from(readPatient.search(new SearchPatientQuery(request.cns(), request.cpf()),
                currentUser.get(), http.getRemoteAddr()), clock);
    }

    @Operation(summary = "Atualizar contato do paciente (REQUESTER, ADMIN)",
            description = "Atualiza apenas os campos informados: telefone, canal preferido e aceite do WhatsApp.")
    @ApiResponse(responseCode = "200", description = "Contato atualizado")
    @ApiResponse(responseCode = "404", description = "Inexistente (PATIENT_NOT_FOUND)", content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "422", description = "Telefone inválido (INVALID_PHONE)", content = @Content(mediaType = "application/problem+json"))
    @PatchMapping("/{id}/contact")
    @PreAuthorize("hasAnyRole('REQUESTER','ADMIN')")
    PatientResponse updateContact(@PathVariable UUID id, @Valid @RequestBody UpdateContactRequest request,
            HttpServletRequest http) {
        return PatientResponse.from(updateContact.update(new UpdatePatientContactCommand(id, request.phone(),
                request.preferredChannel(), request.whatsappOptIn(), currentUser.get(), http.getRemoteAddr())), clock);
    }
}
