package com.finvista.repository;

import com.finvista.model.FinancialAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FinancialAllocationRepository extends JpaRepository<FinancialAllocation, Long> {

    List<FinancialAllocation> findByLancamentoClienteIdAndLancamentoOrigemOrderByLancamentoIdAscBlocoAsc(
            Long clienteId, String origem);

    void deleteByLancamentoIdAndLancamentoClienteId(Long lancamentoId, Long clienteId);

    List<FinancialAllocation> findByLancamentoClienteIdAndLancamentoIdInOrderByLancamentoIdAscBlocoAsc(
            Long clienteId, List<Long> lancamentoIds);
}
