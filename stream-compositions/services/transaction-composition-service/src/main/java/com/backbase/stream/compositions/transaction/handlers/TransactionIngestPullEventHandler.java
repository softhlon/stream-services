package com.backbase.stream.compositions.transaction.handlers;

import com.backbase.buildingblocks.backend.communication.event.EnvelopedEvent;
import com.backbase.buildingblocks.backend.communication.event.handler.EventHandler;
import com.backbase.buildingblocks.backend.communication.event.proxy.EventBus;
import com.backbase.stream.compositions.events.egress.event.spec.v1.TransactionsCompletedEvent;
import com.backbase.stream.compositions.events.egress.event.spec.v1.TransactionsFailedEvent;
import com.backbase.stream.compositions.events.ingress.event.spec.v1.TransactionsPullEvent;
import com.backbase.stream.compositions.transaction.core.config.TransactionConfigurationProperties;
import com.backbase.stream.compositions.transaction.core.mapper.TransactionMapper;
import com.backbase.stream.compositions.transaction.core.model.TransactionIngestPullRequest;
import com.backbase.stream.compositions.transaction.core.model.TransactionIngestResponse;
import com.backbase.stream.compositions.transaction.core.service.TransactionIngestionService;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@AllArgsConstructor
@Slf4j
@EnableConfigurationProperties(TransactionConfigurationProperties.class)
public class TransactionIngestPullEventHandler implements EventHandler<TransactionsPullEvent> {
    private final TransactionConfigurationProperties configProperties;
    private final TransactionIngestionService transactionIngestionService;
    private final TransactionMapper mapper;
    private final EventBus eventBus;

    /**
     * Handles ProductsIngestPullEvent.
     *
     * @param envelopedEvent EnvelopedEvent<ProductsIngestPullEvent>
     */
    @Override
    public void handle(EnvelopedEvent<TransactionsPullEvent> envelopedEvent) {
        buildRequest(envelopedEvent.getEvent())
            .flatMap(transactionIngestionService::ingestPull)
            .doOnError(this::handleError)
            .subscribe(this::handleResponse);
    }


    /**
     * Handles reponse from ingestion service.
     *
     * @param response ProductIngestResponse
     */
    private void handleResponse(TransactionIngestResponse response) {
        if (Boolean.FALSE.equals(configProperties.getEvents().getEnableCompleted())) {
            return;
        }
        TransactionsCompletedEvent event = new TransactionsCompletedEvent()
                .withTransactionIds(response.getTransactions().stream().map(resp -> resp.getId()).collect(Collectors.toList()));
           // .withInternalArrangementId(response);     TODO - mapping!

        EnvelopedEvent<TransactionsCompletedEvent> envelopedEvent = new EnvelopedEvent<>();
        envelopedEvent.setEvent(event);
        eventBus.emitEvent(envelopedEvent);
    }

    /**
     * Handles error from ingestion service.
     *
     * @param ex Throwable
     */
    private void handleError(Throwable ex) {
        log.error("Error ingesting legal entity using the Pull event: {}", ex.getMessage());

        if (Boolean.TRUE.equals(configProperties.getEvents().getEnableFailed())) {
            TransactionsFailedEvent event = new TransactionsFailedEvent()
                .withMessage(ex.getMessage());

            EnvelopedEvent<TransactionsFailedEvent> envelopedEvent = new EnvelopedEvent<>();
            envelopedEvent.setEvent(event);
            eventBus.emitEvent(envelopedEvent);
        }
    }

    /**
     * Builds ingestion request for downstream service.
     *
     * @param event ProductsIngestPullEvent
     * @return ProductIngestPullRequest
     */
    private Mono<TransactionIngestPullRequest> buildRequest(TransactionsPullEvent event) {  // TODO - How event maps - It has list of externalArrangementIds?
        return Mono.just(
                TransactionIngestPullRequest.builder()
                        .build());
    }
}
