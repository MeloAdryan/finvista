package com.finvista.service;
import com.finvista.model.FinancialTransaction;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class ReceiptCompositionTest {
    private FinancialTransaction item(String valor, String pago, String aberto) {
        var t = new FinancialTransaction(); t.setValor(new BigDecimal(valor));
        t.setValorRealizado(pago == null ? null : new BigDecimal(pago));
        t.setValorAberto(aberto == null ? null : new BigDecimal(aberto)); return t;
    }
    private void igual(String esperado, BigDecimal valor) { assertEquals(0, new BigDecimal(esperado).compareTo(valor)); }
    @Test void somaPrincipalConhecidoSemUsarLiquidoComJuros() {
        var t=item("100","40","60");t.setValorTotalRealizado(new BigDecimal("45"));t.setJurosRealizado(new BigDecimal("5"));
        var c=new ReceiptComposition();c.adicionar(t,new BigDecimal("100"));var r=c.resumo();
        igual("40",r.recebido());igual("60",r.emAberto());igual("0",r.semDetalhamento());assertEquals(0,r.registrosSemDetalhamento());
    }
    @Test void zeroInformadoNaoEhAusente() {var c=new ReceiptComposition();c.adicionar(item("100","0","100"),new BigDecimal("100"));igual("0",c.resumo().recebido());igual("100",c.resumo().emAberto());assertEquals(0,c.resumo().registrosSemDetalhamento());}
    @Test void naoDeduzPagamentoDeSaldoAusente() {var c=new ReceiptComposition();c.adicionar(item("100",null,"0"),new BigDecimal("100"));igual("100",c.resumo().semDetalhamento());assertEquals(1,c.resumo().registrosSemDetalhamento());}
    @Test void naoRateiaBaixasDeCategoriaParcial() {var c=new ReceiptComposition();c.adicionar(item("100","40","60"),new BigDecimal("30"));igual("30",c.resumo().semDetalhamento());igual("0",c.resumo().recebido());}
    @Test void divergenciaDeUmCentavoFicaSemDetalhamento() {var c=new ReceiptComposition();c.adicionar(item("100","40","59.99"),new BigDecimal("100"));igual("100",c.resumo().semDetalhamento());}
    @Test void negativoNaoEhInterpretadoComoRecebimento() {var c=new ReceiptComposition();c.adicionar(item("-10","0","-10"),new BigDecimal("-10"));igual("-10",c.resumo().semDetalhamento());assertEquals(1,c.resumo().registrosSemDetalhamento());}
    @Test void canceladoNaoEhRecebidoConhecido() {var t=item("100","100","0");t.setSituacao("CANCELADO");var c=new ReceiptComposition();c.adicionar(t,new BigDecimal("100"));igual("100",c.resumo().semDetalhamento());}
    @Test void contagemNaoSomeQuandoDesconhecidosSeCompensam() {var c=new ReceiptComposition();c.adicionar(item("10",null,null),new BigDecimal("10"));c.adicionar(item("-10",null,null),new BigDecimal("-10"));igual("0",c.resumo().semDetalhamento());assertEquals(2,c.resumo().registrosSemDetalhamento());}
}
