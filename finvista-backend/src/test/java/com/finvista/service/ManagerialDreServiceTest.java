package com.finvista.service;

import com.finvista.model.*;
import com.finvista.repository.*;
import com.finvista.service.FinancialViewService.*;
import com.finvista.service.ManagerialDreService.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagerialDreServiceTest {

    ClienteContextService ctx = mock(ClienteContextService.class);
    FinancialViewService view = mock(FinancialViewService.class);
    DreCategoryMappingRepository repo = mock(DreCategoryMappingRepository.class);
    ManagerialDreService service;
    LocalDate day = LocalDate.of(2026, 9, 1);

    @BeforeEach
    void setup() {
        when(ctx.getClienteAtualId()).thenReturn(7L);
        when(repo.findByClienteIdOrderByTipoAscCategoriaAsc(7L)).thenReturn(List.of());
        var user = mock(Usuario.class);
        when(user.getId()).thenReturn(9L);
        when(ctx.getUsuarioAutenticado()).thenReturn(user);
        service = new ManagerialDreService(ctx, view, repo, Clock.fixed(Instant.parse("2026-10-23T15:00:00Z"), ZoneOffset.UTC));
    }

    Linha line(String type, String category, String amount) {
        return new Linha(1L, "p", "Teste", type, day, day, day, category, "Geral", new BigDecimal(amount));
    }

    void data(List<Linha> rows) {
        when(view.consultar(any())).thenReturn(new Resposta(7L, null, new Faixa(day, day), day, new Total(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), List.of(), rows, Map.of(), List.of(), List.of(), List.of(), List.of(), "PRINCIPAL", ""));
    }

    DreCategoryMapping mapping(String type, String category, Group group) {
        var m = new DreCategoryMapping();
        m.id = UUID.nameUUIDFromBytes(("7:" + ManagerialDreService.key(type, category)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        m.clienteId = 7L;
        m.tipo = type;
        m.categoria = category;
        m.categoriaChave = ManagerialDreService.key(type, category);
        m.grupo = group.name();
        return m;
    }

    @Test
    void noAutomaticClassificationOrDreBeforeReview() {
        data(List.of(line("DESPESA", "Salários", "100")));
        var result = service.report(day, day);
        assertEquals("MAPEAMENTO_PENDENTE", result.estado());
        assertEquals(1, result.naoMapeadas());
        assertTrue(result.demonstrativo().isEmpty());
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void emptyPeriodDoesNotBecomeZeroDemonstrativo() {
        data(List.of());
        var result = service.report(day, day);
        assertEquals("SEM_REGISTROS", result.estado());
        assertTrue(result.demonstrativo().isEmpty());
    }

    @Test
    void validatedMappingProducesArithmeticAndExplicitOutsideAmounts() {
        var categories = List.of(line("RECEITA", "Serviços", "1000"), line("DESPESA", "Dedução", "100"), line("DESPESA", "Custo", "200"), line("DESPESA", "Operação", "300"), line("RECEITA", "Juros", "20"), line("DESPESA", "Tarifa", "10"), line("DESPESA", "Ativo", "50"));
        data(categories);
        when(repo.findByClienteIdOrderByTipoAscCategoriaAsc(7L)).thenReturn(List.of(mapping("RECEITA", "Serviços", Group.RECEITA_OPERACIONAL), mapping("DESPESA", "Dedução", Group.DEDUCOES_RECEITA), mapping("DESPESA", "Custo", Group.CUSTOS), mapping("DESPESA", "Operação", Group.DESPESAS_OPERACIONAIS), mapping("RECEITA", "Juros", Group.RECEITA_FINANCEIRA), mapping("DESPESA", "Tarifa", Group.DESPESA_FINANCEIRA), mapping("DESPESA", "Ativo", Group.FORA_DRE)));
        var result = service.report(day, day);
        assertEquals("MAPEAMENTO_REVISADO", result.estado());
        assertEquals(new BigDecimal("410"), result.demonstrativo().get(9).valor());
        assertEquals(new BigDecimal("50"), result.foraDespesas());
    }

    @Test
    void cannotSaveUnconfirmedMapping() {
        assertThrows(IllegalArgumentException.class, () -> service.save(new MappingRequest(7L, "DESPESA", "A", Group.CUSTOS, false, null, null)));
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void outsideDreRequiresReason() {
        assertThrows(IllegalArgumentException.class, () -> service.save(new MappingRequest(7L, "DESPESA", "A", Group.FORA_DRE, true, null, "")));
    }

    @Test
    void wrongTypeAndCompanyRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.save(new MappingRequest(7L, "RECEITA", "A", Group.CUSTOS, true, null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.save(new MappingRequest(8L, "DESPESA", "A", Group.CUSTOS, true, null, null)));
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void savesReviewedMappingWithoutChangingFinancialTransactions() {
        when(repo.findById(any())).thenReturn(Optional.empty());
        when(repo.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        var result = service.save(new MappingRequest(7L, "DESPESA", "  Custo  ", Group.CUSTOS, true, null, "Conferido"));
        assertEquals(7L, result.clienteId);
        assertEquals(9L, result.revisadoPor);
        assertEquals("Custo", result.categoria);
        verify(ctx).validarAdministrador();
        verifyNoInteractions(view);
    }

    @Test
    void staleVersionRejected() {
        var m = mapping("DESPESA", "A", Group.CUSTOS);
        m.versao = 2;
        when(repo.findById(m.id)).thenReturn(Optional.of(m));
        assertThrows(IllegalArgumentException.class, () -> service.save(new MappingRequest(7L, "DESPESA", "A", Group.DESPESAS_OPERACIONAIS, true, 1L, null)));
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void otherTenantCannotRemoveMapping() {
        var m = mapping("DESPESA", "A", Group.CUSTOS);
        m.clienteId = 8L;
        when(repo.findById(m.id)).thenReturn(Optional.of(m));
        assertThrows(IllegalArgumentException.class, () -> service.remove(m.id, 0));
        verify(repo, never()).delete(any());
    }

    @Test
    void accentsAreNotCollapsedIntoSameMapping() {
        assertNotEquals(ManagerialDreService.key("DESPESA", "Avó"), ManagerialDreService.key("DESPESA", "Avô"));
        assertEquals(ManagerialDreService.key("DESPESA", "A"), ManagerialDreService.key("DESPESA", " a "));
    }

    @Test
    void invalidSavedGroupDoesNotReleaseDre() {
        data(List.of(line("RECEITA", "A", "100")));
        when(repo.findByClienteIdOrderByTipoAscCategoriaAsc(7L)).thenReturn(List.of(mapping("RECEITA", "A", Group.CUSTOS)));
        assertTrue(service.report(day, day).demonstrativo().isEmpty());
    }

    @Test
    void changedCompanyStopsReport() {
        data(List.of());
        when(ctx.getClienteAtualId()).thenReturn(7L, 8L);
        assertThrows(IllegalArgumentException.class, () -> service.report(day, day));
    }
}
