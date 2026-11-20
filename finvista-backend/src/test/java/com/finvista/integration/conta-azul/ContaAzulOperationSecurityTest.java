package com.finvista.integration.contaazul;
import com.finvista.model.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class ContaAzulOperationSecurityTest {
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    @Test void subkeysAreSeparatedAndDeterministic(){
        String k=Base64.getEncoder().encodeToString(new byte[32]);
        assertArrayEquals(ContaAzulKeyDerivation.derive(k,"token-encryption"),ContaAzulKeyDerivation.derive(k,"token-encryption"));
        assertFalse(Arrays.equals(ContaAzulKeyDerivation.derive(k,"token-encryption"),ContaAzulKeyDerivation.derive(k,"preview-signing")));
        assertFalse(Arrays.equals(new byte[32],ContaAzulKeyDerivation.derive(k,"token-encryption")));
    }
    @Test void legacyV1TokensRemainReadable() throws Exception {
        var props=new ContaAzulProperties();props.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        byte[] iv=new byte[12];var c=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        c.init(javax.crypto.Cipher.ENCRYPT_MODE,new javax.crypto.spec.SecretKeySpec(new byte[32],"AES"),new javax.crypto.spec.GCMParameterSpec(128,iv));
        c.updateAAD("2:access".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var cipher=new ContaAzulTokenCipher(props);
        String old="v1."+Base64.getEncoder().encodeToString(iv)+"."+Base64.getEncoder().encodeToString(c.doFinal("legacy".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals("legacy",cipher.decifrar(old,2L,"access"));
        assertTrue(cipher.cifrar("new",2L,"access").startsWith("v2."));
        assertThrows(IllegalStateException.class,()->cipher.decifrar(old,7L,"access"));
        assertThrows(IllegalStateException.class,()->cipher.decifrar(old.replace("v1.","v2."),2L,"access"));
    }
    @Test void changingCompanyInvalidatesConfirmation(){
        var c=new ContaAzulConnection(2L);var now=Instant.parse("2026-11-19T12:00:00Z");
        c.identificar("a","11111111000111","A",now);c.confirmarEmpresa(1L,now);
        c.identificar("a","11111111000111","Novo nome",now.plusSeconds(1));assertNotNull(c.getEmpresaConfirmadaEm());
        c.identificar("b","22222222000122","B",now.plusSeconds(2));assertNull(c.getEmpresaConfirmadaEm());
        c.confirmarEmpresa(1L,now);c.exigirConfirmacaoEmpresa();assertNull(c.getEmpresaVerificadaEm());assertNull(c.getEmpresaConfirmadaEm());
    }
    ContaAzulAutoItem item(String id,int total,int pago,int aberto) throws Exception {
        var i=new ContaAzulAutoItem();i.parcelaId=id;i.tipo="DESPESA";i.status="SEM_ALTERACAO";
        i.resumoJson=json.writeValueAsString(new ContaAzulFinancialClient.Parcela(id,"x",LocalDate.of(2028,1,1),LocalDate.of(2027,12,1),"PENDENTE",null,
            BigDecimal.valueOf(total),BigDecimal.valueOf(pago),BigDecimal.valueOf(aberto),List.of(),List.of()));return i;
    }
    FinancialTransaction row(String id,int pago,int aberto){
        var t=new FinancialTransaction();t.setDocumentoReferencia(id);t.setTipo("DESPESA");t.setDataVencimento(LocalDate.of(2028,1,1));
        t.setValorTotalRealizado(BigDecimal.valueOf(pago));t.setValorTotalAberto(BigDecimal.valueOf(aberto));return t;
    }
    @Test void sameIdsAndMonthlyAmountsAreConferred() throws Exception {
        var r=ContaAzulRunAudit.avaliar(List.of(item("a",100,40,60)),List.of(row("a",40,60)),json);
        assertEquals("CONFERIDO",r.status());assertEquals("2028-01",r.linhas().get(0).mes());
    }
    @Test void sameSumsWithDifferentIdsAreNotConferred() throws Exception {
        assertEquals("DIVERGENTE",ContaAzulRunAudit.avaliar(List.of(item("a",100,40,60)),List.of(row("b",40,60)),json).status());
    }
    @Test void missingSummaryCannotBeConferred() throws Exception {
        var i=item("a",100,40,60);i.resumoJson=null;
        assertEquals("NAO_VERIFICAVEL",ContaAzulRunAudit.avaliar(List.of(i),List.of(row("a",40,60)),json).status());
    }
    @Test void duplicateIdsCannotBeConferred() throws Exception {
        assertEquals("NAO_VERIFICAVEL",ContaAzulRunAudit.avaliar(List.of(item("a",100,40,60),item("a",100,40,60)),List.of(row("a",40,60)),json).status());
    }
    @Test void lossOrRenegotiationNeedsSpecificVerification() throws Exception {
        assertEquals("NAO_VERIFICAVEL",ContaAzulRunAudit.avaliar(List.of(item("a",100,40,0)),List.of(row("a",40,0)),json).status());
    }
    @Test void paidAndOpenMustMatchEvenWhenTotalMatches() throws Exception {
        assertEquals("DIVERGENTE",ContaAzulRunAudit.avaliar(List.of(item("a",100,40,60)),List.of(row("a",60,40)),json).status());
    }
}
