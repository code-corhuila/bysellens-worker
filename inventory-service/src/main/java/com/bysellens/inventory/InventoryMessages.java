package com.bysellens.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Component
class InventoryMessages {
 private final InventoryService inventory; private final ReservaRepository reservas; private final JdbcTemplate db; private final ObjectMapper mapper;
 InventoryMessages(InventoryService inventory,ReservaRepository reservas,JdbcTemplate db,ObjectMapper mapper){this.inventory=inventory;this.reservas=reservas;this.db=db;this.mapper=mapper;}
 @RabbitListener(queues="bysellens.inventory.confirm.v1") @Transactional
 public void confirm(byte[] bytes) throws Exception {
  JsonNode event=mapper.readTree(bytes);long sale=event.path("ventaId").asLong(-1);String id=event.path("eventId").asText();String reservation=event.path("reservaId").asText();
  if(event.path("version").asInt()!=1||sale<=0||!id.equals("confirm-venta-"+sale)||!reservation.equals("venta-"+sale)||!event.path("detalles").isArray()||event.path("detalles").isEmpty()||event.path("fecha").asText().isBlank())throw new IllegalArgumentException("Evento de venta invalido");
  var actual=reservas.lock(reservation).orElseThrow(()->new IllegalStateException("Reserva inexistente"));
  var expected=new ArrayList<InventoryController.Line>();
  for(var line:event.path("detalles")){long product=line.path("productoId").asLong(-1);int quantity=line.path("cantidad").asInt(-1);if(product<=0||quantity<=0)throw new IllegalArgumentException("Detalle invalido");expected.add(new InventoryController.Line(product,quantity));}
  expected.sort(Comparator.comparing(InventoryController.Line::productoId));
  if(!inventory.lines(actual).equals(expected))throw new IllegalArgumentException("Detalle no coincide con la reserva");
  String status=actual.estado;
  if(status.equals("RESERVADA"))status=inventory.transition(reservation,true).get("estado");
  if(!status.equals("CONFIRMADA")&&!status.equals("CANCELADA"))throw new IllegalStateException("Reserva en estado inesperado");
  String payload=mapper.writeValueAsString(Map.of("version",1,"eventId","result-venta-"+sale,"ventaId",sale,"reservaId",reservation,"estado",status));
  db.update("INSERT INTO rabbit_outbox(id,routing_key,payload) VALUES (?,?,?) ON CONFLICT (id) DO NOTHING","result-venta-"+sale,"result",payload);
 }
}
