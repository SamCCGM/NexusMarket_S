# Domain Services

## Overview

Los servicios de dominio representan las operaciones que el Marketplace permite realizar. Cada servicio debe respetar las reglas de negocio y las responsabilidades de los roles autorizados.

Los servicios se organizan en las siguientes áreas:

- Administración de usuarios.
- Gestión de compradores y vendedores.
- Administración de bodegas.
- Gestión de productos y catálogo.
- Gestión de inventario.
- Gestión del carrito.
- Gestión de pedidos y pagos.
- Facturación.
- Gestión de envíos.
- Gestión de devoluciones y reembolsos.
- Reportes administrativos.

---

## User Services

| Service        | Main Actor    | Description                                                                                     | Main Entity |
| -------------- | ------------- | ----------------------------------------------------------------------------------------------- | ----------- |
| `UpdateUser`   | Administrator | Actualiza la información general permitida de un usuario existente.                             | User        |
| `BlockUser`    | Administrator | Bloquea a un usuario para impedir que participe en operaciones que requieran una cuenta activa. | User        |
| `ActivateUser` | Administrator | Reactiva a un usuario previamente bloqueado.                                                    | User        |

### Business Rules

- Cada usuario debe tener un identificador único.
- El correo electrónico debe ser único.
- Cada usuario debe tener exactamente un rol válido y un estado válido.
- El registro de compradores y vendedores se documenta en sus respectivas secciones; por eso no se mantiene un servicio genérico `RegisterUser` que duplique esos flujos.
- Un usuario bloqueado no puede realizar operaciones que requieran una cuenta activa.
- El bloqueo de un usuario no debe borrar su historial ni sus pedidos anteriores.

---

## Buyer Services

| Service         | Main Actor             | Description                                                                                                                                                                    | Main Entity |
| --------------- | ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------- |
| `RegisterBuyer` | Administrator / System | Registra un comprador y crea su perfil de comprador. El registro público puede iniciarlo el comprador si así lo permite la aplicación; el sistema valida y persiste los datos. | Buyer       |
| `UpdateBuyer`   | Buyer / Administrator  | Actualiza la información del comprador. El comprador solo puede modificar su propia información y el administrador puede hacerlo dentro de sus responsabilidades.              | Buyer       |

### Business Rules

- El comprador debe estar registrado y activo para realizar operaciones de compra.
- El perfil de comprador debe cumplir las reglas generales de User.
- Un comprador bloqueado no puede iniciar nuevas compras ni modificar un carrito para completar una compra.
- La información de direcciones debe ser válida para el uso que se le dará.
- El registro de comprador no debe duplicar la creación de una cuenta genérica: crea un usuario con rol `BUYER` y su información específica.

---

## Seller Services

| Service          | Main Actor             | Description                                                                                              | Main Entity |
| ---------------- | ---------------------- | -------------------------------------------------------------------------------------------------------- | ----------- |
| `RegisterSeller` | Administrator          | Incorpora un vendedor al Marketplace.                                                                    | Seller      |
| `UpdateSeller`   | Administrator / Seller | Actualiza la información permitida del vendedor. El vendedor solo puede modificar su propia información. | Seller      |
| `BlockSeller`    | Administrator          | Bloquea la participación de un vendedor en el Marketplace.                                               | Seller      |
| `ActivateSeller` | Administrator          | Reactiva a un vendedor previamente bloqueado.                                                            | Seller      |

### Business Rules

- Los vendedores no pueden registrarse por sí mismos como vendedores del Marketplace.
- La incorporación de un vendedor debe realizarla un administrador.
- Un vendedor bloqueado no puede publicar productos ni realizar operaciones que requieran un vendedor activo.
- Los productos y el historial de pedidos del vendedor deben conservarse al bloquearlo.
- Un vendedor solo puede actualizar sus propios datos y administrar sus propios productos.

---

## Warehouse Services

| Service             | Main Actor    | Description                                                 | Main Entity |
| ------------------- | ------------- | ----------------------------------------------------------- | ----------- |
| `RegisterWarehouse` | Administrator | Registra una bodega en el sistema.                          | Warehouse   |
| `UpdateWarehouse`   | Administrator | Actualiza la información permitida de una bodega existente. | Warehouse   |

### Business Rules

- Cada bodega debe tener un identificador único y una ubicación válida.
- Cada bodega debe tener un tipo válido definido por `WarehouseType`.
- `WarehouseType` distingue las bodegas del Marketplace (`MARKETPLACE`) de las asociadas a vendedores (`SELLER`).
- El inventario de una bodega se administra mediante sus registros `Inventory`; la bodega puede contener varios registros de inventario.
- Una bodega no debe eliminarse si hacerlo deja pedidos, envíos o registros de inventario en un estado inconsistente. La forma de desactivarla, si se necesita, se definirá en el modelo.

---

## Product Services

| Service              | Main Actor             | Description                                                                             | Main Entity |
| -------------------- | ---------------------- | --------------------------------------------------------------------------------------- | ----------- |
| `RegisterProduct`    | Seller                 | Registra un producto en el catálogo del vendedor.                                       | Product     |
| `UpdateProduct`      | Seller                 | Actualiza la información permitida de un producto propio.                               | Product     |
| `PublishProduct`     | Seller                 | Publica un producto para permitir que aparezca disponible en el catálogo.               | Product     |
| `SuspendProduct`     | Administrator / Seller | Cambia el estado del producto a suspendido, si el actor tiene autorización sobre él.    | Product     |
| `DiscontinueProduct` | Administrator / Seller | Cambia el estado del producto a descontinuado, si el actor tiene autorización sobre él. | Product     |

### Business Rules

- Todo producto debe estar asociado a un vendedor.
- El producto debe cumplir los datos obligatorios de su tipo antes de registrarse o publicarse.
- El estado debe pertenecer a `ProductStatus`.
- Un producto suspendido o descontinuado no puede incluirse en nuevas compras.
- Un vendedor solo puede administrar sus propios productos; el administrador puede actuar dentro de sus responsabilidades.
- Publicar un producto no garantiza que tenga unidades disponibles. La disponibilidad de productos físicos depende del inventario disponible.
- Los productos físicos requieren gestión de inventario y envío. Los productos virtuales pueden entregarse digitalmente después de confirmar el pago y no deben generar un envío físico.

---

## Inventory Services

| Service        | Main Actor                 | Description                                                                                      | Main Entity |
| -------------- | -------------------------- | ------------------------------------------------------------------------------------------------ | ----------- |
| `AddStock`     | LogisticsOperator / Seller | Registra el ingreso de unidades a un registro de inventario de una bodega autorizada.            | Inventory   |
| `ReserveStock` | System                     | Reserva unidades disponibles durante el proceso de compra, después de validar la disponibilidad. | Inventory   |
| `RemoveStock`  | System / LogisticsOperator | Registra la salida de unidades por una venta o por otra operación autorizada.                    | Inventory   |
| `AdjustStock`  | LogisticsOperator          | Corrige la cantidad registrada después de una verificación o ajuste autorizado.                  | Inventory   |
| `ReturnStock`  | LogisticsOperator          | Registra el retorno al inventario de unidades devueltas que puedan volver a estar disponibles.   | Inventory   |

### Business Rules

- La cantidad disponible no puede ser negativa.
- No se puede reservar una cantidad superior a la disponible.
- No se puede reservar inventario inexistente.
- La disponibilidad debe validarse de nuevo al crear el pedido y antes de confirmar el pago; agregar un producto al carrito no reserva unidades por sí solo.
- Las operaciones de inventario deben identificar la bodega y el producto correspondientes.
- Las unidades dañadas o no aptas para la venta no pueden considerarse disponibles para reserva. El modelo de inventario debe representar esta distinción antes de implementar esta regla.
- La reserva, la salida por venta, el ajuste y el retorno deben actualizar las cantidades de forma consistente para evitar vender las mismas unidades dos veces.
- `ReturnStock` solo debe volver a sumar como disponible la cantidad que haya sido revisada y declarada apta para la venta.

---

## Cart Services

| Service              | Main Actor | Description                                                                                                                  | Main Entity     |
| -------------------- | ---------- | ---------------------------------------------------------------------------------------------------------------------------- | --------------- |
| `AddItemToCart`      | Buyer      | Agrega un producto al carrito del comprador o actualiza la línea existente según la regla definida para productos repetidos. | Cart / CartItem |
| `UpdateCartItem`     | Buyer      | Actualiza la cantidad de una línea del carrito propio.                                                                       | CartItem        |
| `RemoveItemFromCart` | Buyer      | Elimina una línea del carrito propio.                                                                                        | CartItem        |

### Business Rules

- Cada carrito pertenece a un comprador.
- Cada `CartItem` debe referenciar un producto existente.
- La cantidad solicitada debe ser mayor que cero.
- Solo pueden agregarse productos aptos para nuevas compras y de vendedores que puedan participar en el Marketplace.
- Se puede consultar la disponibilidad al modificar el carrito, pero la validación definitiva debe repetirse durante la creación del pedido.
- Los productos eliminados del carrito no forman parte del pedido que se cree posteriormente.
- El comprador solo puede modificar su propio carrito.

---

## Order Services

| Service            | Main Actor        | Description                                                                                                                                                            | Main Entity      |
| ------------------ | ----------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------- |
| `CreateOrder`      | Buyer / System    | Crea un pedido a partir del contenido vigente del carrito después de validar los productos, cantidades y disponibilidad. El pedido inicia en `PENDING_PAYMENT`.        | Order            |
| `ProcessPayment`   | Buyer / System    | Registra el resultado de la operación de pago. El pedido pasa a `PAID` únicamente si el pago se confirma correctamente.                                                | Order            |
| `DispatchShipment` | LogisticsOperator | Despacha el envío físico asociado al pedido y actualiza el estado del pedido a `DISPATCHED` cuando corresponda. La operación detallada se define en Shipment Services. | Shipment / Order |
| `ConfirmDelivery`  | LogisticsOperator | Registra que el envío físico llegó a su destino y actualiza el pedido a `DELIVERED`.                                                                                   | Shipment / Order |
| `FinalizeOrder`    | System            | Cambia el pedido de `DELIVERED` a `FINALIZED` cuando se cumple la condición de finalización definida por el sistema.                                                   | Order            |

### Order Lifecycle

```text
PENDING_PAYMENT
       │
       │ pago confirmado
       ▼
     PAID
       │
       │ despacho físico
       ▼
  DISPATCHED
       │
       │ entrega confirmada
       ▼
   DELIVERED
       │
       │ finalización automática
       ▼
   FINALIZED
```

### Business Rules

- El pedido se crea a partir del contenido vigente del carrito; `ConfirmOrder` no se mantiene como servicio separado porque duplicaría la confirmación que ocurre al crear el pedido.
- El pedido debe conservar únicamente los productos incluidos en la compra y sus cantidades acordadas.
- Los productos y cantidades deben volver a validarse al crear el pedido. Si no hay disponibilidad suficiente, no se debe crear un pedido comprable con cantidades inválidas.
- El pedido solo pasa a `PAID` si el pago se confirma. Un intento fallido no debe marcarse como pagado.
- Los pedidos físicos requieren envío y confirmación de entrega antes de finalizarse.
- Los productos virtuales no requieren envío físico; su entrega digital ocurre después de confirmar el pago. El flujo para finalizar pedidos virtuales debe respetar los estados definidos y quedar reflejado en la implementación.
- `DISPATCHED` solo corresponde a pedidos que efectivamente requieren despacho físico.
- Un pedido finalizado no puede modificarse.
- Las transiciones deben respetar `OrderStatus`; no se permiten saltos arbitrarios entre estados.
- `DispatchShipment` se documenta como la operación que coordina el despacho del envío y la actualización del pedido; no se conserva un servicio duplicado llamado `DispatchOrder`.

---

## Billing Services

| Service           | Main Actor | Description                                                                           | Main Entity |
| ----------------- | ---------- | ------------------------------------------------------------------------------------- | ----------- |
| `GenerateInvoice` | System     | Genera la información de facturación correspondiente a un pedido con pago confirmado. | Order       |

### Business Rules

- La facturación debe estar asociada a un pedido válido.
- La información de facturación debe corresponder al pedido y a los importes registrados.
- La factura no debe generarse como si el pago hubiera sido exitoso cuando el pago está pendiente o fallido.
- Para la primera entrega, la información de facturación se documenta como resultado asociado al pedido; no se presupone una entidad `Invoice` independiente hasta confirmar que se incorporará al modelo.

---

## Shipment Services

| Service            | Main Actor                 | Description                                                                                                     | Main Entity      |
| ------------------ | -------------------------- | --------------------------------------------------------------------------------------------------------------- | ---------------- |
| `CreateShipment`   | LogisticsOperator / System | Crea un envío para un pedido físico pagado que necesita despacho.                                               | Shipment         |
| `DispatchShipment` | LogisticsOperator          | Registra la salida del envío hacia su destino y actualiza el pedido asociado a `DISPATCHED` cuando corresponda. | Shipment / Order |
| `ConfirmDelivery`  | LogisticsOperator          | Registra la entrega del envío en su destino y actualiza el pedido asociado a `DELIVERED`.                       | Shipment / Order |

### Business Rules

- Un envío debe estar asociado a un pedido válido que incluya productos físicos.
- El envío debe tener un destino válido.
- Solo se debe despachar un envío creado para un pedido cuyo pago haya sido confirmado.
- No se deben crear envíos físicos para pedidos compuestos únicamente por productos virtuales.
- La confirmación de entrega debe registrarse antes de finalizar el pedido.
- `ConfirmDelivery` se incluye una sola vez en el documento y se aplica tanto al envío como al estado del pedido relacionado.

---

## Return Services

| Service         | Main Actor    | Description                                                                                     | Main Entity |
| --------------- | ------------- | ----------------------------------------------------------------------------------------------- | ----------- |
| `RequestReturn` | Buyer         | Registra una solicitud de devolución relacionada con una compra realizada por el comprador.     | Return      |
| `ApproveReturn` | Administrator | Aprueba una solicitud de devolución después de verificar que cumple las condiciones aplicables. | Return      |

### Business Rules

- La devolución debe estar relacionada con un pedido existente del comprador que la solicita.
- La solicitud debe identificar el pedido y describir el motivo.
- La devolución debe respetar las condiciones definidas para el proceso de devoluciones.
- Solo una devolución aprobada puede pasar al proceso de reembolso.
- La aprobación de una devolución no implica que las unidades regresen automáticamente al inventario: primero deben recibirse y revisarse cuando corresponda.
- Si se requiere devolver solo una parte de un pedido, el modelo debe identificar los productos y cantidades devueltos. Si `Return` solo referencia el pedido y el motivo, esta limitación debe resolverse en la revisión del modelo antes de implementar devoluciones parciales.

---

## Refund Services

| Service         | Main Actor             | Description                                                                | Main Entity |
| --------------- | ---------------------- | -------------------------------------------------------------------------- | ----------- |
| `ProcessRefund` | Administrator / System | Registra o procesa el reembolso correspondiente a una devolución aprobada. | Refund      |

### Business Rules

- El reembolso debe estar asociado a una devolución existente y aprobada.
- El reembolso debe corresponder a un pedido con pago confirmado.
- No se debe procesar un reembolso dos veces por la misma devolución e importe.
- La operación debe registrar el resultado del proceso. No se presupone una integración con una pasarela de pagos externa en esta primera entrega.
- El reembolso y la reincorporación de unidades al inventario son operaciones relacionadas, pero distintas: solo se actualiza el inventario cuando las unidades hayan sido recibidas y declaradas aptas.

---

## Reporting Services

| Service                        | Main Actor    | Description                                                                          | Main Entity |
| ------------------------------ | ------------- | ------------------------------------------------------------------------------------ | ----------- |
| `GenerateAdministrativeReport` | Administrator | Genera información para la consulta y el seguimiento administrativo del Marketplace. | Domain data |

### Business Rules

- Solo los roles autorizados pueden consultar o generar reportes administrativos; en esta versión, el actor autorizado es Administrator.
- La información del reporte debe corresponder a los datos registrados por el sistema.
- Los reportes no deben modificar las entidades consultadas.
- Los reportes pueden incluir información de usuarios, vendedores, productos, inventario, pedidos, envíos, devoluciones y reembolsos según el alcance del reporte solicitado.

---

## Service Relationships

```text
Administrator
│
├── UpdateUser ───────────────> User
├── BlockUser ────────────────> User
├── ActivateUser ────────────> User
├── RegisterBuyer ────────────> Buyer
├── RegisterSeller ───────────> Seller
├── UpdateSeller ─────────────> Seller
├── BlockSeller ──────────────> Seller
├── ActivateSeller ───────────> Seller
├── RegisterWarehouse ────────> Warehouse
├── UpdateWarehouse ──────────> Warehouse
├── SuspendProduct ───────────> Product
├── DiscontinueProduct ───────> Product
├── ApproveReturn ────────────> Return
└── GenerateAdministrativeReport

Seller
│
├── UpdateSeller ─────────────> Seller
├── RegisterProduct ──────────> Product
├── UpdateProduct ────────────> Product
├── PublishProduct ───────────> Product
├── SuspendProduct ───────────> Product
├── DiscontinueProduct ───────> Product
└── AddStock ─────────────────> Inventory

Buyer
│
├── UpdateBuyer ──────────────> Buyer
├── AddItemToCart ────────────> Cart / CartItem
├── UpdateCartItem ───────────> CartItem
├── RemoveItemFromCart ───────> CartItem
├── CreateOrder ──────────────> Order
└── RequestReturn ────────────> Return

LogisticsOperator
│
├── AddStock ─────────────────> Inventory
├── AdjustStock ──────────────> Inventory
├── ReturnStock ──────────────> Inventory
├── CreateShipment ───────────> Shipment
├── DispatchShipment ─────────> Shipment / Order
└── ConfirmDelivery ──────────> Shipment / Order

System
│
├── ReserveStock ─────────────> Inventory
├── RemoveStock ──────────────> Inventory
├── ProcessPayment ───────────> Order
├── FinalizeOrder ────────────> Order
└── GenerateInvoice ──────────> Order

Order
│
├── CreateShipment ───────────> Shipment
├── RequestReturn ────────────> Return
│                                │
│                                ├── ApproveReturn
│                                └── ProcessRefund ──> Refund
└── GenerateInvoice
```

---
