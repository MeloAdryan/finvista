package com.finvista.repository;
import com.finvista.model.DreCategoryMapping;import java.util.*;import org.springframework.data.jpa.repository.JpaRepository;
public interface DreCategoryMappingRepository extends JpaRepository<DreCategoryMapping,UUID>{List<DreCategoryMapping> findByClienteIdOrderByTipoAscCategoriaAsc(Long clienteId);}
