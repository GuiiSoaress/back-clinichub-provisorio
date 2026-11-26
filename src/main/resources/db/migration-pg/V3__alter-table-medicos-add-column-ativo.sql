alter table medicos add ativo boolean not null default true;
update medicos set ativo = true;
