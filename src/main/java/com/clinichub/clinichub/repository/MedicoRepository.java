package com.clinichub.clinichub.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clinichub.clinichub.model.entity.Medico;

public interface MedicoRepository extends JpaRepository<Medico, Long>{
    
}
