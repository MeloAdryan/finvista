package com.finvista.service;
import com.finvista.model.FinancialTransaction;
import java.math.BigDecimal;import java.time.*;import java.time.temporal.ChronoUnit;import java.util.*;

public final class FinancialOpenItems {
 private FinancialOpenItems(){}
 public enum Bucket {HOJE,DIAS_1_30,DIAS_31_60,DIAS_61_90,MAIS_90,A_VENCER,SEM_SALDO,SEM_VENCIMENTO}
 public record Item(Long id,String documento,String descricao,String tipo,String situacao,LocalDate competencia,LocalDate vencimento,BigDecimal aberto,BigDecimal principalAberto,String origem,Bucket faixa) {}
 public static List<Item> read(List<FinancialTransaction> source,LocalDate today){
  List<Item> result=new ArrayList<>();
  for(var t:source){String type=t.getTipo()==null?"":t.getTipo().trim().toUpperCase(Locale.ROOT);if(!Set.of("RECEITA","DESPESA").contains(type)||!FinancialStatusPolicy.ativo(t))continue;
   var value=t.getValorTotalAberto();if(value!=null&&value.signum()==0)continue;
   if(value!=null&&(value.signum()<0||value.stripTrailingZeros().scale()>2))value=null;
   var due=t.getDataVencimento();if(due!=null&&(due.getYear()<1900||due.getYear()>2200))due=null;
   Bucket bucket;
   if(value==null)bucket=Bucket.SEM_SALDO;else if(due==null)bucket=Bucket.SEM_VENCIMENTO;else {long late=ChronoUnit.DAYS.between(due,today);bucket=late<0?Bucket.A_VENCER:late==0?Bucket.HOJE:late<=30?Bucket.DIAS_1_30:late<=60?Bucket.DIAS_31_60:late<=90?Bucket.DIAS_61_90:Bucket.MAIS_90;}
   result.add(new Item(t.getId(),t.getDocumentoReferencia(),t.getDescricao(),type,t.getSituacao(),t.getDataCompetencia(),due,value,t.getValorAberto(),t.getOrigem(),bucket));
  }
  result.sort(Comparator.comparing(Item::vencimento,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Item::id,Comparator.nullsLast(Comparator.naturalOrder())));return List.copyOf(result);
 }
}
