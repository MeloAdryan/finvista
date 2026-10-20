package com.finvista.integration.contaazul;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContaAzulSettlementPolicyTest {
    BigDecimal v(String s) { return s == null ? null : new BigDecimal(s); }
    ContaAzulParcelDetail.Composicao c(String bruto,String juros,String multa,String desconto,String taxa) {
        BigDecimal liquido = bruto==null || juros==null || multa==null || desconto==null ? null
                : v(bruto).add(v(juros)).add(v(multa)).subtract(v(desconto));
        return new ContaAzulParcelDetail.Composicao(v(bruto),liquido,v(juros),v(multa),v(desconto),v(taxa));
    }
    ContaAzulParcelDetail.Baixa baixa(String id,String parcela,String data,ContaAzulParcelDetail.Composicao c) {
        return new ContaAzulParcelDetail.Baixa(id,parcela,data==null?null:LocalDate.parse(data),c);
    }
    ContaAzulParcelDetail p(String status,String pago,String pendente,ContaAzulParcelDetail.Composicao c,
            List<ContaAzulParcelDetail.Baixa> baixas) {
        return new ContaAzulParcelDetail("parcela","Consulta",status,LocalDate.parse("2026-10-21"),
                c.liquido(),v(pago),v(pendente),c,null,null,null,baixas);
    }
    @Test void somaMultiplasBaixasSemDuplicarPrincipalEMantemUltimaData() {
        var comp=c("100","5","2","3","0");
        var p=p("RECEBIDO_PARCIAL","53","51",comp,List.of(
                baixa("b2","parcela","2026-10-19",c("30","2","1","0","0")),
                baixa("b1","parcela","2026-10-18",c("20","1","0","1","0"))));
        var f=ContaAzulSettlementPolicy.conferir(p);
        assertEquals(v("50.00"),f.principalPago()); assertEquals(v("53.00"),f.totalPago());
        assertEquals(v("50.00"),f.principalAberto()); assertEquals(v("51.00"),f.totalAberto());
        assertEquals(LocalDate.parse("2026-10-19"),f.ultimaBaixa());
    }
    @Test void pagamentoExigeBaixasExplicitasEConsistentes() {
        for(var b:Arrays.asList(null,List.<ContaAzulParcelDetail.Baixa>of()))
            assertThrows(IllegalArgumentException.class,()->ContaAzulSettlementPolicy.conferir(p("QUITADO","100","0",c("100","0","0","0","0"),b)));
    }
    @Test void baixasDuplicadasDeOutraParcelaSemDataOuNulasBloqueiam() {
        var b=baixa("b","parcela","2026-10-19",c("100","0","0","0","0"));
        for(var list:List.of(List.of(b,b),List.of(baixa("b","outra","2026-10-19",b.composicao())),
                List.of(baixa("b","parcela",null,b.composicao())),Arrays.asList((ContaAzulParcelDetail.Baixa)null)))
            assertThrows(IllegalArgumentException.class,()->ContaAzulSettlementPolicy.conferir(p("QUITADO","100","0",c("100","0","0","0","0"),list)));
    }
    @Test void componentesAusentesNegativosTaxaENaoFechamentoBloqueiam() {
        for(var comp:List.of(c("100",null,"0","0","0"),c("100","-1","0","0","0"),
                c("100","0","0","0","1"),c("100.001","0","0","0","0")))
            assertThrows(IllegalArgumentException.class,()->ContaAzulSettlementPolicy.conferir(p("PENDENTE","0","100",comp,List.of())));
        assertThrows(IllegalArgumentException.class,()->ContaAzulSettlementPolicy.conferir(p("PENDENTE","0","99",c("100","0","0","0","0"),List.of())));
    }
    @Test void situacoesEspeciaisESaldoIncompativelNaoSaoConvertidos() {
        for(String status:List.of("CANCELADO","RENEGOCIADO","PERDIDO","DESCONHECIDO","QUITADO","RECEBIDO_PARCIAL"))
            assertThrows(IllegalArgumentException.class,()->ContaAzulSettlementPolicy.conferir(p(status,"0","100",c("100","0","0","0","0"),List.of())));
    }
    @Test void somaPagaOuPrincipalRealizadoDivergenteBloqueia() {
        var b=baixa("b","parcela","2026-10-19",c("101","0","0","0","0"));
        assertThrows(IllegalArgumentException.class,()->ContaAzulSettlementPolicy.conferir(p("QUITADO","100","0",c("100","0","0","0","0"),List.of(b))));
    }
}
