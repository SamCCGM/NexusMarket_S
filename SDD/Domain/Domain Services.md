# Domain Services

Los Domain Services representan operaciones del dominio que requieren coordinar
diferentes entidades o conceptos y que no pertenecen naturalmente a una única
entidad.

No todas las operaciones del sistema deben convertirse en Domain Services.

Cuando una operación puede ser realizada de manera coherente por una entidad
del dominio, esta debe mantener la responsabilidad sobre dicha operación.

Los principales Domain Services identificados para NexusMarket son:

- `PurchaseService`
- `OrderFulfillmentService`
- `LogisticsService`
- `ReturnService`

---

# PurchaseService

## Description

`PurchaseService` representa la operación de confirmación de una compra.

Su responsabilidad principal es coordinar el proceso que transforma la
selección realizada mediante un `Cart` en un `Order`.

La compra requiere la interacción entre diferentes elementos del dominio, por
lo que no pertenece exclusivamente a `Cart`, `Product` o `Order`.

---

## Responsibilities

Las principales responsabilidades de `PurchaseService` son:

- Validar la selección realizada en el carrito.
- Verificar la disponibilidad de los productos físicos.
- Crear el pedido correspondiente.
- Generar los elementos del pedido a partir de los productos efectivamente
  seleccionados.
- Iniciar el proceso de pago.
- Mantener la consistencia entre el carrito y el pedido generado.

---

## Collaborators

```text
Buyer
   │
   └── owns ─────────────> Cart
                              │
                              └── contains ──> CartItem
                                                   │
                                                   └── Product
                                                        │
                                                        ▼
                                              PurchaseService
                                                        │
                                                        ▼
                                                      Order
                                                        │
                                                        └── OrderItem
```

## Business Rules

- La compra debe realizarse a partir de los productos actualmente contenidos en el carrito.
- Los productos eliminados del carrito antes de confirmar la compra no forman parte del pedido.
- La disponibilidad del inventario debe verificarse antes de confirmar la compra.
- Los productos físicos requieren inventario disponible.
- La compra debe generar un pedido formal.

## Design Notes

PurchaseService coordina varias entidades, pero no debe almacenar permanentemente información propia del pedido.

El Cart mantiene la selección provisional y el Order mantiene el resultado de la compra confirmada.

Las reglas relacionadas exclusivamente con el inventario permanecen dentro de Inventory.

---

# OrderFulfillmentService

## Description

`OrderFulfillmentService` representa la coordinación del cumplimiento de un pedido después de la confirmación de la compra.

Su función es coordinar las diferentes etapas necesarias para que el pedido avance desde el pago confirmado hasta su finalización.

El ciclo definido para el pedido contempla las etapas:

```text
PendingPayment
      │
      ▼
    Paid
      │
      ▼
 Dispatched
      │
      ▼
Delivered / Finalized
```

## Responsibilities

Las principales responsabilidades de OrderFulfillmentService son:

- Coordinar el procesamiento de pedidos pagados.
- Coordinar el alistamiento de productos físicos.
- Coordinar la salida de productos desde el inventario.
- Coordinar la preparación del despacho.
- Coordinar el avance del pedido hacia su entrega o finalización.
- Diferenciar el procesamiento de productos físicos y digitales.

## Collaborators

```text
Order
   │
   ├── contains ─────────> OrderItem
   │                          │
   │                          └── Product
   │
   ├── uses ─────────────> Inventory
   │
   └── may generate ─────> Shipment

OrderFulfillmentService
   │
   ├── coordinates ──────> Order
   ├── coordinates ──────> Inventory
   └── coordinates ──────> Shipment
```

## Business Rules

- El pedido debe avanzar de acuerdo con su ciclo de vida.
- Un pedido pagado puede iniciar el proceso de alistamiento.
- Los productos físicos deben ser alistados antes del despacho.
- La salida del inventario debe producirse como parte del proceso de venta.
- Los productos digitales pueden ser entregados inmediatamente después de la confirmación del pago.
- Los productos físicos requieren un proceso de despacho y entrega.

## Design Notes

Las operaciones específicas sobre las existencias continúan perteneciendo a Inventory.

OrderFulfillmentService únicamente coordina estas operaciones con el pedido y el proceso logístico.

De esta manera se evita que Order tenga que conocer directamente todos los detalles necesarios para gestionar inventario y logística.

---

# LogisticsService

## Description

`LogisticsService` representa la coordinación de las operaciones necesarias para el despacho y entrega de productos físicos.

La logística incluye las actividades de preparación, empaque, despacho, transporte y entrega.

## Responsibilities

Las principales responsabilidades de `LogisticsService` son:

- Coordinar la preparación de productos físicos.
- Coordinar el empaque del pedido.
- Coordinar el despacho.
- Coordinar el transporte.
- Coordinar la entrega al destinatario.
- Actualizar el progreso logístico relacionado con el pedido.

## Collaborators

```text
Order
   │
   └── generates ─────────> Shipment
                              │
                              └── delivered to ──> Address

LogisticsService
   │
   ├── coordinates ──────> Order
   ├── manages ──────────> Shipment
   └── interacts with ───> Warehouse
```

## Business Rules

- Los productos físicos requieren despacho.
- Los productos digitales no requieren envío físico.
- El proceso logístico comienza después de la confirmación correspondiente del pedido.
- El proceso logístico comprende preparación, empaque, despacho, transporte y entrega.
- La entrega debe realizarse al destinatario correspondiente.

## Design Notes

LogisticsService coordina el proceso logístico, pero no reemplaza a Warehouse, Inventory o Shipment.

Cada entidad mantiene la información que le corresponde:

- Warehouse representa la bodega.
- Inventory representa las existencias.
- Shipment representa el envío.
- LogisticsService coordina el proceso entre ellas.

---

# ReturnService

## Description

`ReturnService` representa la coordinación del proceso de devolución y su posible reembolso.

La devolución pertenece a los procesos de posventa del Marketplace y puede involucrar diferentes elementos del dominio, como el pedido, el inventario y el reembolso.

## Responsibilities

Las principales responsabilidades de `ReturnService` son:

- Coordinar una solicitud de devolución.
- Validar la relación de la devolución con el pedido original.
- Coordinar el ingreso de productos devueltos al inventario cuando corresponda.
- Coordinar el proceso de reembolso cuando corresponda.
- Mantener la trazabilidad entre pedido, devolución y reembolso.

## Collaborators

```text
Order
   │
   └── may generate ─────> Return
                              │
                              ├── may affect ─────> Inventory
                              │
                              └── may generate ──> Refund

ReturnService
   │
   ├── coordinates ──────> Order
   ├── coordinates ──────> Return
   ├── coordinates ──────> Inventory
   └── coordinates ──────> Refund
```

## Business Rules

- Una devolución debe estar relacionada con una compra existente.
- El proceso de devolución debe mantener la trazabilidad del pedido original.
- Una devolución puede generar un reembolso.
- Cuando corresponda, los productos devueltos deben reflejarse en el inventario.
- Las reglas específicas para aceptar una devolución y calcular un reembolso deberán definirse cuando existan requisitos funcionales más detallados.

## Design Notes

ReturnService se encarga de coordinar el proceso y no de almacenar la información propia de la devolución.

Las entidades Return, Refund, Order e Inventory mantienen sus respectivas responsabilidades.
