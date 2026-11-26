package com.clinichub.clinichub.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.clinichub.clinichub.enums.Especialidade;
import com.clinichub.clinichub.model.entity.Medico;

public interface MedicoRepository extends JpaRepository<Medico, Long> {

    Page<Medico> findAllByAtivoTrue(Pageable paginacao);

    @Query("""
            select m.ativo from Medico m
            where m.id = :id
            """)
    Boolean findAtivoById(@Param("id") Long id);

    @Query("""
            select m from Medico m
            where m.ativo = true
            and (:especialidade is null or m.especialidade = :especialidade)
            and not exists (
                select 1 from Consulta c
                where c.medico = m
                and c.data > :inicioJanela
                and c.data < :fimJanela
                and c.motivoCancelamento is null
            )
            """)
    List<Medico> buscarMedicosDisponiveisNaData(
            @Param("especialidade") Especialidade especialidade,
            @Param("inicioJanela") LocalDateTime inicioJanela,
            @Param("fimJanela") LocalDateTime fimJanela
    );

}
