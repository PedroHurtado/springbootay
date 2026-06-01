package com.example.demo.core.dispatcher;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/** Enruta cada consulta a su {@link QueryHandler}. Análogo a {@link CommandDispatcher}. */
@Service
public class QueryDispatcher {

    private final Map<Class<?>, QueryHandler<?, ?>> handlers;

    public QueryDispatcher(List<QueryHandler<?, ?>> handlers) {
        this.handlers = handlers.stream().collect(toMap(QueryHandler::queryType, identity()));
    }

    @SuppressWarnings("unchecked")
    public <Q, R> R dispatch(Q query) {
        QueryHandler<Q, R> handler = (QueryHandler<Q, R>) handlers.get(query.getClass());
        if (handler == null) {
            throw new NoHandlerRegisteredException(query.getClass());
        }
        return handler.handle(query);
    }
}
