package br.com.caelum.camel;

import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

public class RotaPedidos {

	public static void main(String[] args) throws Exception {

		CamelContext context = new DefaultCamelContext();
		context.addComponent("activemq", ActiveMQComponent.activeMQComponent("tcp://localhost:61616"));
		context.addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {

				errorHandler(
						deadLetterChannel("activemq:queue:pedidos.DLQ"). //usando DLQ
								logExhaustedMessageHistory(true).
								maximumRedeliveries(3).
								redeliveryDelay(2000).
								onRedelivery(new Processor() {
									@Override
									public void process(Exchange exchange) throws Exception {
										int counter = (int) exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER);
										int max = (int) exchange.getIn().getHeader(Exchange.REDELIVERY_MAX_COUNTER);
										System.out.println("Redelivery - " + counter + "/" + max );
									}
								})
				);

				from("activemq:queue:pedidos").
						routeId("rota-pedidos").
						to("validator:pedido.xsd").
						multicast().
						to("direct:soap").
						to("direct:http");

				from("direct:soap").
						routeId("rota-soap").
//						log("chamando servico soap ${body}").
						to("xslt:pedido-para-soap.xslt").
						log("Resultado do Template: ${body}").
						setHeader(Exchange.CONTENT_TYPE, constant("text/xml")).
						to("http4://localhost:8080/webservices/financeiro");

				from("direct:http").
						routeId("rota-http").
						setProperty("pedidoId", xpath("/pedido/id/text()")).
						setProperty("email", xpath("/pedido/pagamento/email-titular/text()")).
						split().
						xpath("/pedido/itens/item").
						filter().
						xpath("/item/formato[text()='EBOOK']").
						setProperty("ebookId", xpath("/item/livro/codigo/text()")).
						setHeader(Exchange.HTTP_QUERY,
								simple("clienteId=${property.email}&pedidoId=${property.pedidoId}&ebookId=${property.ebookId}")).
						to("http4://localhost:8080/webservices/ebook/item");

//				from("file:pedidos?delay=5s&noop=true").
//						setProperty("pedidoId", xpath("/pedido/id/text()")).
//						setProperty("clienteId", xpath("/pedido/pagamento/email-titular/text()")).
//						split().
//						xpath("/pedido/itens/item").
//						filter().
//						xpath("/item/formato[text()='EBOOK']").
//						setProperty("ebookId", xpath("/item/livro/codigo/text()")).
//						log("${id} \n ${body}").
//						marshal().
//						xmljson().
//						setHeader(Exchange.HTTP_QUERY,
//								simple("clienteId=${property.clienteId}&pedidoId=${property.pedidoId}&ebookId=${property.ebookId}")).
//						to("http4://localhost:8080/webservices/ebook/item");
			}
		});

		context.start();
		Thread.sleep(10000);
		context.stop();
	}	
}
