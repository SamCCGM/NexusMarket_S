# Domain Services

## Overview

Los servicios de dominio representan las principales operaciones que el sistema proporciona para ejecutar acciones del Marketplace.

Cada servicio define una acción concreta sobre una o más entidades del dominio y debe respetar las reglas de negocio correspondientes.

Los servicios se organizan de acuerdo con las principales áreas funcionales del sistema:

- Administración de usuarios.
- Gestión de compradores.
- Gestión de vendedores.
- Administración de bodegas.
- Gestión de productos y catálogo.
- Gestión de inventario.
- Gestión del carrito.
- Gestión de pedidos.
- Facturación.
- Gestión de envíos.
- Gestión de devoluciones.
- Gestión de reembolsos.
- Consulta de reportes administrativos.

---

## User Services

| Service        | Main Actor    | Description                                                                              | Main Entity |
| -------------- | ------------- | ---------------------------------------------------------------------------------------- | ----------- |
| `RegisterUser` | Administrator | Registra un nuevo usuario en el sistema.                                                 | `User`      |
| `UpdateUser`   | Administrator | Actualiza la información de un usuario existente.                                        | `User`      |
| `BlockUser`    | Administrator | Cambia el estado de un usuario para impedir su participación en operaciones del sistema. | `User`      |
| `ActivateUser` | Administrator | Reactiva un usuario previamente bloqueado.                                               | `User`      |

### Business Rules

- Cada usuario debe tener un identificador único.
- El correo electrónico debe ser único.
- Un usuario debe tener un rol y un estado válido.
- Un usuario bloqueado no puede participar normalmente en las operaciones del sistema.

---

## Buyer Services

| Service         | Main Actor            | Description                                            | Main Entity |
| --------------- | --------------------- | ------------------------------------------------------ | ----------- |
| `RegisterBuyer` | Administrator         | Registra un comprador en el sistema.                   | `Buyer`     |
| `UpdateBuyer`   | Buyer / Administrator | Actualiza la información correspondiente al comprador. | `Buyer`     |

### Business Rules

- El comprador debe estar registrado antes de realizar operaciones de compra.
- La información del comprador debe cumplir las reglas de identificación definidas para los usuarios.
- Un comprador bloqueado no puede realizar nuevas operaciones de compra.

---

## Seller Services

| Service          | Main Actor             | Description                                                | Main Entity |
| ---------------- | ---------------------- | ---------------------------------------------------------- | ----------- |
| `RegisterSeller` | Administrator          | Incorpora un nuevo vendedor al Marketplace.                | `Seller`    |
| `UpdateSeller`   | Administrator / Seller | Actualiza la información del vendedor.                     | `Seller`    |
| `BlockSeller`    | Administrator          | Bloquea la participación de un vendedor en el Marketplace. | `Seller`    |
| `ActivateSeller` | Administrator          | Reactiva un vendedor previamente bloqueado.                | `Seller`    |

### Business Rules

- Los vendedores no pueden registrarse por sí mismos.
- El vendedor debe ser incorporado por un Administrador.
- Un vendedor bloqueado no puede realizar operaciones que requieran un vendedor activo.

---

## Warehouse Services

| Service             | Main Actor    | Description                                       | Main Entity |
| ------------------- | ------------- | ------------------------------------------------- | ----------- |
| `RegisterWarehouse` | Administrator | Registra una nueva bodega dentro del sistema.     | `Warehouse` |
| `UpdateWarehouse`   | Administrator | Actualiza la información de una bodega existente. | `Warehouse` |

### Business Rules

- Cada bodega debe tener un identificador único.
- La bodega debe tener una ubicación válida.
- Cada bodega debe pertenecer a un tipo definido por `WarehouseType`.
- El tipo de bodega permite distinguir entre bodegas del Marketplace y bodegas asociadas a vendedores.

---

## Product Services

| Service              | Main Actor             | Description                                                         | Main Entity |
| -------------------- | ---------------------- | ------------------------------------------------------------------- | ----------- |
| `RegisterProduct`    | Seller                 | Registra un producto dentro del catálogo del vendedor.              | `Product`   |
| `UpdateProduct`      | Seller                 | Actualiza la información de un producto registrado.                 | `Product`   |
| `PublishProduct`     | Seller                 | Publica un producto para que pueda estar disponible en el catálogo. | `Product`   |
| `SuspendProduct`     | Administrator / Seller | Cambia el estado del producto a suspendido.                         | `Product`   |
| `DiscontinueProduct` | Administrator / Seller | Cambia el estado del producto a descontinuado.                      | `Product`   |

### Business Rules

- Un producto debe estar asociado a un vendedor.
- El producto debe contener la información requerida para su registro.
- El producto debe utilizar un estado válido definido por `ProductStatus`.
- Un producto suspendido no debe estar disponible para nuevas compras.
- Un producto descontinuado no debe volver a estar disponible para nuevas compras.

---

## Inventory Services

| Service        | Main Actor                 | Description                                                                          | Main Entity |
| -------------- | -------------------------- | ------------------------------------------------------------------------------------ | ----------- |
| `AddStock`     | LogisticsOperator / Seller | Registra el ingreso de unidades de un producto en una bodega.                        | `Inventory` |
| `ReserveStock` | System                     | Reserva unidades de un producto para una operación de compra.                        | `Inventory` |
| `RemoveStock`  | System / LogisticsOperator | Registra la salida de unidades del inventario como consecuencia de una venta.        | `Inventory` |
| `AdjustStock`  | LogisticsOperator          | Corrige la cantidad registrada en el inventario.                                     | `Inventory` |
| `ReturnStock`  | LogisticsOperator          | Registra nuevamente en el inventario las unidades correspondientes a una devolución. | `Inventory` |

### Business Rules

- La cantidad disponible nunca puede ser negativa.
- No se puede reservar una cantidad superior a la disponible.
- No se puede reservar inventario inexistente.
- Un producto marcado como no disponible no puede reservarse.
- Las operaciones de inventario deben estar asociadas a una bodega y un producto.

---

## Cart Services

| Service              | Main Actor | Description                                                      | Main Entity         |
| -------------------- | ---------- | ---------------------------------------------------------------- | ------------------- |
| `AddItemToCart`      | Buyer      | Agrega un producto al carrito del comprador.                     | `Cart` / `CartItem` |
| `UpdateCartItem`     | Buyer      | Actualiza la cantidad de un producto seleccionado en el carrito. | `CartItem`          |
| `RemoveItemFromCart` | Buyer      | Elimina un producto seleccionado del carrito.                    | `CartItem`          |

### Business Rules

- El carrito pertenece a un comprador.
- Cada elemento del carrito debe referenciar un producto existente.
- La cantidad solicitada debe ser mayor que cero.
- La cantidad solicitada debe poder ser cubierta por el inventario disponible.
- Los productos eliminados del carrito no forman parte del pedido final.

---

## Order Services

| Service          | Main Actor        | Description                                                             | Main Entity |
| ---------------- | ----------------- | ----------------------------------------------------------------------- | ----------- |
| `CreateOrder`    | Buyer             | Genera un pedido a partir de los productos seleccionados en el carrito. | `Order`     |
| `ConfirmOrder`   | Buyer             | Confirma el pedido para iniciar el proceso de compra.                   | `Order`     |
| `ProcessPayment` | Buyer / System    | Registra el pago correspondiente al pedido.                             | `Order`     |
| `DispatchOrder`  | LogisticsOperator | Registra el despacho del pedido.                                        | `Order`     |
| `FinalizeOrder`  | System            | Finaliza el pedido después de completar su ciclo de entrega.            | `Order`     |

### Order Lifecycle

```text
PENDING_PAYMENT
       │
       ▼
     PAID
       │
       ▼
  DISPATCHED
       │
       ▼
   DELIVERED
       │
       ▼
   FINALIZED
```

### Business Rules (continuación)

- Un pedido se genera a partir del contenido confirmado del carrito.
- El pedido debe conservar únicamente los productos que fueron incluidos en la compra.
- Un pedido finalizado no puede modificarse.
- Las transiciones de estado deben respetar el ciclo definido para los pedidos.

---

## Billing Services

| Service           | Main Actor | Description                                                        | Main Entity |
| ----------------- | ---------- | ------------------------------------------------------------------ | ----------- |
| `GenerateInvoice` | System     | Genera la información de facturación correspondiente a una compra. | Order       |

### Business Rules

- La facturación debe estar asociada a una compra válida.
- La información de la factura debe corresponder al pedido realizado.

La entidad Invoice podrá incorporarse posteriormente al modelo cuando se implemente de forma completa el dominio de facturación.

---

## Shipment Services

| Service            | Main Actor        | Description                                                 | Main Entity |
| ------------------ | ----------------- | ----------------------------------------------------------- | ----------- |
| `CreateShipment`   | LogisticsOperator | Crea el envío asociado a un pedido que debe ser despachado. | Shipment    |
| `DispatchShipment` | LogisticsOperator | Registra el despacho del envío hacia su destino.            | Shipment    |

### Business Rules

- Un envío debe estar asociado a un pedido.
- El envío debe tener un destino válido.
- El despacho debe realizarse sobre un pedido que pueda ser enviado.

---

## Return Services

| Service         | Main Actor | Description                                               | Main Entity |
| --------------- | ---------- | --------------------------------------------------------- | ----------- |
| `RequestReturn` | Buyer      | Solicita la devolución de un pedido o producto adquirido. | Return      |

### Business Rules

- La devolución debe estar relacionada con una compra existente.
- La solicitud debe identificar el pedido correspondiente.
- La devolución debe respetar las condiciones definidas para el proceso de devoluciones.

---

## Refund Services

| Service         | Main Actor             | Description                                                     | Main Entity |
| --------------- | ---------------------- | --------------------------------------------------------------- | ----------- |
| `ProcessRefund` | Administrator / System | Procesa el reembolso correspondiente a una devolución aprobada. | Refund      |

### Business Rules

- El reembolso debe estar asociado a una devolución existente.
- El reembolso debe corresponder a una operación de compra válida.
- El proceso debe respetar las condiciones establecidas para los reembolsos.

---

## Reporting Services

| Service                        | Main Actor                 | Description                                                                   | Main Entity |
| ------------------------------ | -------------------------- | ----------------------------------------------------------------------------- | ----------- |
| `GenerateAdministrativeReport` | Administrator / Supervisor | Genera información para la consulta y seguimiento administrativo del sistema. | Domain data |

### Business Rules

- El acceso a los reportes debe estar limitado a los roles autorizados.
- La información presentada debe corresponder a los datos registrados por el sistema.

---

## Service Relationships

```text
User
│
├── RegisterUser
├── UpdateUser
├── BlockUser
└── ActivateUser
        │
        └── manages ──────> User


Administrator
│
├── RegisterSeller ───────> Seller
├── BlockSeller ──────────> Seller
├── ActivateSeller ───────> Seller
└── RegisterWarehouse ────> Warehouse


Seller
│
├── RegisterProduct ──────> Product
├── UpdateProduct ────────> Product
└── PublishProduct ───────> Product


LogisticsOperator
│
├── AddStock ─────────────> Inventory
├── AdjustStock ──────────> Inventory
├── ReturnStock ──────────> Inventory
└── DispatchShipment ─────> Shipment


Buyer
│
├── AddItemToCart ────────> Cart
├── UpdateCartItem ───────> CartItem
├── RemoveItemFromCart ───> CartItem
└── CreateOrder ──────────> Order


Order
│
├── ProcessPayment
├── DispatchOrder
├── FinalizeOrder
├── GenerateInvoice
└── CreateShipment ───────> Shipment


Order
│
└── RequestReturn ────────> Return
                              │
                              └── ProcessRefund ──────> Refund
```
