create table consultas(

    id bigserial primary key,
    medico_id bigint not null,
    paciente_id bigint not null,
    data timestamp not null,
    motivo_cancelamento varchar(100),

    constraint fk_consultas_medico_id foreign key(medico_id) references medicos(id),
    constraint fk_consultas_paciente_id foreign key(paciente_id) references pacientes(id)

);
