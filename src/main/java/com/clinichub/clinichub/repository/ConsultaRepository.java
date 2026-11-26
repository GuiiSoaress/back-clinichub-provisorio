package com.clinichub.clinichub.repository;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.clinichub.clinichub.model.entity.Consulta;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {

    boolean existsByPacienteIdAndDataBetweenAndMotivoCancelamentoIsNull(
            Long idPaciente,
            LocalDateTime primeiroHorario,
            LocalDateTime ultimoHorario
    );

    boolean existsByMedicoIdAndDataGreaterThanAndDataLessThanAndMotivoCancelamentoIsNull(
            Long idMedico,
            LocalDateTime inicioJanela,
            LocalDateTime fimJanela
    );

    Page<Consulta> findAllByMotivoCancelamentoIsNull(Pageable paginacao);

}
