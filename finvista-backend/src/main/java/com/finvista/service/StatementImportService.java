package com.finvista.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvista.model.StatementMovement;
import com.finvista.repository.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StatementImportService {
    public record Preview(Long clienteId, String clienteNome, String arquivo, int total, int novas,
            int existentes, List<String> conflitos, String arquivoHash, String estadoHash) {}
    public record Imported(Long clienteId, int incluidas, int existentes, int total) {}
    private record Item(String key, int occurrence, ContaAzulStatementService.Line line) {}
    private final ContaAzulStatementService reader;
    private final StatementMovementRepository movements;
    private final ClienteRepository clients;
    private final ClienteContextService context;
    private final ObjectMapper json;
    private final Clock clock;
    public StatementImportService(ContaAzulStatementService reader, StatementMovementRepository movements,
            ClienteRepository clients, ClienteContextService context, ObjectMapper json, Clock clock) {
        this.reader=reader; this.movements=movements; this.clients=clients;
        this.context=context; this.json=json; this.clock=clock;
    }
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (IOException e) { throw new IllegalStateException("Falha ao preservar os dados do extrato.",e); }
    }
    public ContaAzulStatementService.Line decode(StatementMovement value) {
        try { return json.readValue(value.getConteudo(), ContaAzulStatementService.Line.class); }
        catch (IOException e) { throw new IllegalStateException("Movimentação importada não pôde ser lida: " + value.getId(),e); }
    }
    private String core(ContaAzulStatementService.Line l) {
        return encode(List.of(l.movimento().toString(), l.descricao().trim(),
                ContaAzulStatementService.normalize(l.tipo()), ContaAzulStatementService.normalize(l.origem()),
                l.conta().trim(), l.campos().getOrDefault("identificador do fornecedor/cliente", ""),
                l.campos().getOrDefault("nome do fornecedor/cliente", "")));
    }
    private String content(ContaAzulStatementService.Line l) {
        return encode(Arrays.asList(l.competencia(), l.vencimento(), l.valor(), l.categorias(), l.centros(), l.rateios()));
    }
    private List<Item> items(ContaAzulStatementService.Result read) {
        if(!read.reconhecido() || read.linhas().isEmpty()) throw new IllegalArgumentException("Selecione um extrato financeiro com movimentações.");
        Map<String,Integer> occurrences=new HashMap<>(); List<Item> result=new ArrayList<>();
        for(var line:read.linhas()) {
            if(!line.erros().isEmpty() || line.movimento()==null || line.valor()==null)
                throw new IllegalArgumentException("Corrija os erros da linha " + line.numero() + " antes de importar. Nenhum dado foi gravado.");
            if(line.movimento().getYear()<1900 || line.movimento().getYear()>2200)
                throw new IllegalArgumentException("Data fora de 1900–2200 na linha " + line.numero());
            String key=hash(core(line)+"|"+line.valor().toPlainString());
            result.add(new Item(key,occurrences.merge(key,1,Integer::sum),line));
        }
        return result;
    }
    private String state(List<StatementMovement> saved) {
        return hash(encode(saved.stream().map(x->x.getChave()+":"+x.getOcorrencia()+":"+hash(x.getConteudo())).sorted().toList()));
    }
    private Preview preview(MultipartFile file, List<Item> items, List<StatementMovement> saved) throws IOException {
        var client=context.getClienteAtual();
        Map<String,StatementMovement> keys=new HashMap<>(); Map<String,Set<String>> cores=new HashMap<>();
        for(var old:saved) {
            keys.put(old.getChave()+":"+old.getOcorrencia(),old);
            cores.computeIfAbsent(core(decode(old)),k->new HashSet<>()).add(old.getChave());
        }
        int fresh=0,existing=0; List<String> conflicts=new ArrayList<>();
        for(var item:items) {
            var old=keys.get(item.key()+":"+item.occurrence());
            if(old!=null) {
                existing++;
                if(!content(decode(old)).equals(content(item.line())))
                    conflicts.add("Linha " + item.line().numero()+": movimentação já importada com datas ou rateios diferentes. Revise antes de substituir.");
            } else {
                fresh++;
                var similar=cores.get(core(item.line()));
                if(similar!=null && !similar.contains(item.key()))
                    conflicts.add("Linha " + item.line().numero()+": possível correção de valor de uma movimentação existente. Inclusão bloqueada para revisão.");
            }
        }
        return new Preview(client.getId(),client.getNome(),file.getOriginalFilename(),items.size(),fresh,existing,
                List.copyOf(conflicts),hash(Base64.getEncoder().encodeToString(file.getBytes())),state(saved));
    }
    @Transactional(readOnly=true)
    public Preview plan(MultipartFile file) throws IOException {
        Long client=context.getClienteAtualId();
        var result=preview(file,items(reader.inspect(file)),movements.findByClienteIdOrderByIdAsc(client));
        if(!client.equals(result.clienteId())) throw new IllegalArgumentException("A empresa mudou. Gere outra prévia.");
        return result;
    }
    @Transactional(readOnly=true)
    public ContaAzulStatementService.Result stored(Long client) {
        if(!client.equals(context.getClienteAtualId())) throw new IllegalArgumentException("A empresa mudou. Consulte novamente.");
        List<ContaAzulStatementService.Line> rows=new ArrayList<>();
        for(var saved:movements.findByClienteIdOrderByIdAsc(client)) {
            var l=decode(saved); var fields=new LinkedHashMap<>(l.campos());
            fields.put("arquivo de importacao",saved.getArquivo()); fields.put("linha no arquivo",String.valueOf(l.numero()));
            rows.add(new ContaAzulStatementService.Line(rows.size()+1,l.movimento(),l.competencia(),l.vencimento(),l.descricao(),
                    l.tipo(),l.origem(),l.conta(),l.situacao(),l.valor(),l.categorias(),l.centros(),l.erros(),l.rateios(),fields));
        }
        if(!client.equals(context.getClienteAtualId())) throw new IllegalArgumentException("A empresa mudou. Consulte novamente.");
        return new ContaAzulStatementService.Result(true,"Extratos já incluídos nesta empresa",List.copyOf(rows));
    }
    @Transactional
    public Imported apply(MultipartFile file, Preview expected) throws IOException {
        Long client=context.getClienteAtualId();
        if(!client.equals(expected.clienteId())) throw new IllegalArgumentException("A empresa mudou. Gere outra prévia.");
        var locked=clients.bloquearAtivoPorId(client).orElseThrow(()->new IllegalArgumentException("Empresa indisponível."));
        var items=items(reader.inspect(file)); var saved=movements.findByClienteIdOrderByIdAsc(client);
        var current=preview(file,items,saved);
        if(!current.arquivoHash().equals(expected.arquivoHash()) || !current.estadoHash().equals(expected.estadoHash()))
            throw new IllegalArgumentException("O arquivo ou a base mudou. Gere e confira outra prévia.");
        if(!current.conflitos().isEmpty()) throw new IllegalArgumentException("Há conflitos na prévia. Nenhuma movimentação foi incluída.");
        Set<String> keys=new HashSet<>(); saved.forEach(x->keys.add(x.getChave()+":"+x.getOcorrencia()));
        List<StatementMovement> fresh=new ArrayList<>();
        for(var item:items) if(keys.add(item.key()+":"+item.occurrence()))
            fresh.add(new StatementMovement(locked,item.key(),item.occurrence(),encode(item.line()),file.getOriginalFilename(),clock.instant()));
        if(!client.equals(context.getClienteAtualId())) throw new IllegalArgumentException("A empresa mudou. Gere outra prévia.");
        movements.saveAllAndFlush(fresh);
        return new Imported(client,fresh.size(),current.existentes(),current.total());
    }
}
