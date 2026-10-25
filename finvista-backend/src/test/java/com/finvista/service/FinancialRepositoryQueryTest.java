package com.finvista.service;

import com.finvista.repository.ContaAzulAutoItemRepository;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.parser.PartTree;
import static org.junit.jupiter.api.Assertions.*;

class FinancialRepositoryQueryTest {

    @Test
    void validatesLatestPendingQueryAndDreMappingWithoutConnectingToDatabase() throws Exception {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.hbm2ddl.auto", "none").build();
        try {
            var sources = new MetadataSources(registry);
            for (var type : new Class<?>[]{com.finvista.model.AdminClientChange.class, com.finvista.model.Budget.class, com.finvista.model.CashFlow.class, com.finvista.model.Cliente.class, com.finvista.model.ContaAzulApiOperation.class, com.finvista.model.ContaAzulAutoItem.class, com.finvista.model.ContaAzulConnection.class, com.finvista.model.ContaAzulSourceCapture.class, com.finvista.model.ContaAzulSyncBatch.class, com.finvista.model.CostCategoryClassification.class, com.finvista.model.CostCenter.class, com.finvista.model.DreCategoryMapping.class, com.finvista.model.ExpenseDistribution.class, com.finvista.model.FinancialAlertRead.class, com.finvista.model.FinancialAlertRecord.class, com.finvista.model.FinancialAllocation.class, com.finvista.model.FinancialHistory.class, com.finvista.model.FinancialTransaction.class, com.finvista.model.SpendingGoal.class, com.finvista.model.StatementMovement.class, com.finvista.model.Usuario.class}) {
                sources.addAnnotatedClass(type);
            }
            try (var factory = (SessionFactoryImplementor) sources.buildMetadata().buildSessionFactory()) {
                var method = ContaAzulAutoItemRepository.class.getMethod("findTerminalByCliente", Long.class);
                String hql = method.getAnnotation(Query.class).value();
                assertNotNull(factory.getQueryEngine().getHqlTranslator().translate(hql, com.finvista.model.ContaAzulAutoItem.class));
                assertNotNull(new PartTree("findFirstByClienteIdAndStatusInOrderByConcluidoEmDesc", com.finvista.model.ContaAzulAutoRun.class));
                assertNotNull(new PartTree("findByClienteIdOrderByTipoAscCategoriaAsc", com.finvista.model.DreCategoryMapping.class));
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
