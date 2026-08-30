# Domain Model

## Introduction

El Domain Model representa las principales entidades del negocio de NexusMarket
y las relaciones existentes entre ellas.

NexusMarket es una plataforma digital centralizada que actúa como intermediario
comercial entre compradores y vendedores. El sistema administra los procesos
relacionados con el registro de usuarios, productos, inventario, compras,
pedidos, logística, facturación, devoluciones y reembolsos.

El modelo de dominio representa los conceptos principales necesarios para
describir estos procesos y sus relaciones. Se utiliza herencia cuando existe
una especialización clara entre conceptos del dominio y asociaciones explícitas
para representar las relaciones entre entidades independientes.

El modelo distingue principalmente entre:

- Usuarios y sus diferentes roles dentro del Marketplace.
- Productos físicos y productos digitales.
- Bodegas e inventario distribuido.
- Carritos y elementos del carrito.
- Pedidos y elementos comprados.
- Procesos logísticos y comerciales.
- Devoluciones y reembolsos.

El sistema contempla cinco tipos de participantes principales:

- Buyer
- Seller
- LogisticsOperator
- Administrator
- Supervisor

Cada participante tiene un único rol dentro del sistema y solamente puede
interactuar con la información correspondiente a sus responsabilidades.

---

# General Domain Model

El modelo general del dominio puede representarse mediante las siguientes
jerarquías y relaciones principales:

```text
User
   │
   ├── Buyer
   │
   ├── Seller
   │
   ├── Administrator
   │
   ├── LogisticsOperator
   │
   └── Supervisor


Product
   │
   ├── PhysicalProduct
   │
   └── DigitalProduct


Seller
   ├── manages ──────────────> Product
   │
   └── owns ─────────────────> Warehouse


PhysicalProduct
   │
   └── has ──────────────────> Inventory
                                  │
                                  └── stored in ───────> Warehouse


Buyer
   ├── owns ─────────────────> Cart
   │                              │
   │                              └── contains ───────> CartItem
   │                                                        │
   │                                                        └── references ──> Product
   │
   └── creates ──────────────> Order
                                  │
                                  └── contains ───────> OrderItem
                                                            │
                                                            └── references ──> Product


Order
   ├── generates ────────────> Shipment
   │
   ├── generates ────────────> Invoice
   │
   └── may generate ─────────> Return
                                  │
                                  └── generates ──────> Refund

```

## Main Domain Flow

El flujo principal del negocio puede resumirse de la siguiente manera:

```text
Administrator
   │
   └── registers ────────────> Seller
                                  │
                                  └── has ───────────> Warehouse
                                                         │
                                                         └── stores ──> Inventory


Seller
   │
   └── registers ────────────> Product
                                  │
                                  ├── PhysicalProduct
                                  │       │
                                  │       └── requires ──> Inventory
                                  │
                                  └── DigitalProduct


Buyer
   │
   └── selects products ─────> Cart
                                  │
                                  └── contains ───────> CartItem
                                                         │
                                                         └── Product
                                                              │
                                                              ▼
                                                            Order
                                                              │
                                      ┌───────────────────────┼───────────────────────┐
                                      │                       │                       │
                                      ▼                       ▼                       ▼
                                   Invoice                 Shipment                Return
                                                                                     │
                                                                                     ▼
                                                                                  Refund
```

---

# User (Abstract)

## Description

`User` representa a cualquier participante autorizado que interactúa con el
sistema NexusMarket.

La entidad User constituye la base para los diferentes participantes del
Marketplace y permite representar la información común que comparten todos
los roles del sistema.

User es una clase abstracta porque representa un concepto general de usuario.
Las responsabilidades específicas se encuentran en sus clases especializadas:

- Buyer
- Seller
- Administrator
- LogisticsOperator
- Supervisor

La clase `User` no debe ser instanciada directamente. Cada usuario debe
pertenecer a una única especialización y tener un único rol dentro del sistema.

El rol determina las responsabilidades y permisos que tiene el usuario dentro
del Marketplace.

---

## Attributes

| Attribute | Type         | Description                                                             |
| --------- | ------------ | ----------------------------------------------------------------------- |
| `id`      | `String`     | Identificador único del usuario dentro de la plataforma.                |
| `name`    | `String`     | Nombre completo y oficial del usuario.                                  |
| `email`   | `String`     | Correo electrónico utilizado como medio de acceso y comunicación.       |
| `role`    | `UserRole`   | Define las responsabilidades y permisos del usuario dentro del sistema. |
| `status`  | `UserStatus` | Representa la condición operativa actual del usuario.                   |

---

## Attribute Details

### id

Identifica de forma única al usuario dentro de la plataforma.

**Restrictions:**

- Es obligatorio.
- Debe ser único.

### name

Representa el nombre completo y oficial del usuario.

**Restrictions:**

- Es obligatorio.
- No puede estar vacío.

### email

Representa el correo electrónico utilizado como medio principal de acceso y
comunicación.

**Restrictions:**

- Es obligatorio.
- Debe ser único dentro de la plataforma.

### role

Define las responsabilidades y permisos asociados al usuario.

Los roles definidos por NexusMarket son:

- `BUYER`
- `SELLER`
- `LOGISTICS_OPERATOR`
- `ADMINISTRATOR`
- `SUPERVISOR`

Cada usuario debe tener un único rol.

### status

Representa la condición operativa del usuario dentro del sistema.

El estado debe pertenecer al catálogo de estados definido por el sistema. Entre
los estados contemplados se encuentra, por ejemplo, `ACTIVE` y `BLOCKED`.

---

## Relationships

`User` es la clase base de los diferentes participantes del Marketplace.

```text
User
   │
   ├── Buyer
   │
   ├── Seller
   │
   ├── Administrator
   │
   ├── LogisticsOperator
   │
   └── Supervisor
```

---

# Buyer

## Description

`Buyer` representa al usuario que adquiere productos publicados dentro del
Marketplace.

Esta clase especializa a `User` y contiene la información específica necesaria
para que un usuario pueda participar en los procesos comerciales de compra.

El comprador puede seleccionar productos mediante un carrito y posteriormente
confirmar un pedido.

El comprador no puede administrar información perteneciente a otros
compradores ni administrar inventarios.

---

## Attributes

| Attribute             | Type               | Description                                                                  |
| --------------------- | ------------------ | ---------------------------------------------------------------------------- |
| `primaryAddress`      | `Address`          | Ubicación habitual utilizada para las entregas del comprador.                |
| `additionalAddresses` | `List<Address>`    | Conjunto de ubicaciones secundarias que pueden utilizarse para las entregas. |
| `commercialStatus`    | `CommercialStatus` | Condición actual del comprador para realizar compras.                        |

---

## Attribute Details

### primaryAddress

Representa la dirección principal asociada al comprador y corresponde a la
ubicación habitual para las entregas.

**Restrictions:**

- Es obligatoria.
- Debe representar una ubicación válida para realizar entregas.

### additionalAddresses

Representa las ubicaciones secundarias que el comprador puede utilizar para
recibir sus pedidos.

**Restrictions:**

- Es opcional.
- Puede contener cero o más direcciones adicionales.

### commercialStatus

Representa la condición comercial del comprador para realizar compras.

**Restrictions:**

- Es obligatorio.
- Debe pertenecer a los estados comerciales definidos por el sistema.

---

## Relationships

```text
User
   │
   └── Buyer
         │
         ├── owns ───────────────> Cart
         │                            │
         │                            └── contains ──> CartItem
         │
         └── creates ────────────> Order
                                      │
                                      └── contains ──> OrderItem
```

Un `Buyer` es una especialización de `User`. Un comprador puede tener un carrito activo y puede realizar múltiples pedidos durante su participación en el Marketplace.

### User identification

- Cada usuario debe tener un identificador único.
- Cada usuario debe tener un nombre completo.
- El nombre completo no puede estar vacío.
- Cada usuario debe tener un correo electrónico.
- El correo electrónico debe ser único dentro de la plataforma.
- El documento de identidad debe ser único dentro de la plataforma.

### Responsibilities

Las principales responsabilidades de `Buyer` son:

- Mantener la información específica necesaria para participar en procesos comerciales.
- Gestionar sus direcciones de entrega.
- Seleccionar productos mediante el carrito.
- Confirmar pedidos.
- Consultar la información relacionada con sus procesos de compra.

### Design Notes

- `Buyer` hereda de `User` porque comparte la información común de todos los participantes del sistema, pero posee información y responsabilidades específicas relacionadas con la compra.
- La información de las direcciones no se coloca directamente en `User`, ya que no todos los participantes del Marketplace necesitan información de entrega.

### Identity document

La especificación establece que el documento de identidad debe ser único dentro
de la plataforma. Sin embargo, la tabla de atributos del dominio de usuarios
presenta únicamente el atributo `Identificador`.

Por esta razón, el modelo mantiene `id` como identificador de la entidad y
deja pendiente determinar si el documento de identidad debe representarse como
un atributo independiente en una versión posterior del modelo.

---

# Seller

## Description

`Seller` representa al usuario responsable de registrar y administrar los productos que comercializa dentro del Marketplace.

Los vendedores son incorporados administrativamente por un `Administrator` y no pueden registrarse por sí mismos. El vendedor registra productos, define sus características y administra la información relacionada con sus productos.

## Attributes

En el modelo actual no se identifican atributos propios adicionales para `Seller` que deban almacenarse además de los heredados de `User`. La información relacionada con los productos y bodegas se representa mediante relaciones con otras entidades del dominio.

## Relationships

```text
User
   │
   └── Seller
         │
         ├── manages ──────────> Product
         │
         └── owns ──────────────> Warehouse
```

Un Buyer es una especialización de User.

Un comprador puede tener un carrito activo y puede realizar múltiples pedidos durante su participación en el Marketplace.

### Business Rules

- El comprador debe estar registrado como usuario del sistema.
- El comprador debe tener una dirección principal.
- Las direcciones adicionales son opcionales.
- El comprador debe tener un estado comercial válido.
- El comprador puede seleccionar productos mediante el carrito.
- El comprador puede confirmar pedidos.
- El comprador no puede administrar información de otros compradores.
- El comprador no puede administrar inventarios.

### Responsibilities

Las principales responsabilidades de Buyer son:

- Mantener la información específica necesaria para participar en procesos comerciales.
- Gestionar sus direcciones de entrega.
- Seleccionar productos mediante el carrito.
- Confirmar pedidos.
- Consultar la información relacionada con sus procesos de compra.

### Design Notes

Buyer hereda de User porque comparte la información común de todos los participantes del sistema, pero posee información y responsabilidades específicas relacionadas con la compra.

La información de las direcciones no se coloca directamente en User, ya que no todos los participantes del Marketplace necesitan información de entrega.

---

## Seller

### Description

Seller representa al usuario responsable de registrar y administrar los productos que comercializa dentro del Marketplace.

Los vendedores son incorporados administrativamente por un Administrator y no pueden registrarse por sí mismos.

El vendedor registra productos, define sus características y administra la información relacionada con sus productos.

### Attributes

En el modelo actual no se identifican atributos propios adicionales para Seller que deban almacenarse además de los heredados de User.

La información relacionada con los productos y bodegas se representa mediante relaciones con otras entidades del dominio.

### Relationships

```
User
   │
   └── Seller
         │
         ├── manages ──────────> Product
         │
         └── owns ──────────────> Warehouse
```

Un Seller puede administrar múltiples productos.

Un Seller posee al menos una bodega asociada dentro del modelo actual, ya que la incorporación de un vendedor contempla el registro de su primera bodega.

### Business Rules

- Los vendedores no pueden auto-registrarse.
- Los vendedores deben ser incorporados por un Administrator.
- El vendedor registra y administra sus productos.
- El vendedor puede definir las características de sus productos.
- Las bodegas asociadas al vendedor deben ser administradas de acuerdo con las reglas correspondientes al dominio de bodegas.
- El vendedor no puede administrar información fuera de las responsabilidades asociadas a su rol.

### Responsibilities

Las principales responsabilidades de Seller son:

- Administrar sus productos.
- Registrar productos en el catálogo.
- Definir las características de sus productos.
- Gestionar la información relacionada con sus bodegas.
- Participar en la operación comercial de los productos que ofrece.

### Design Notes

No se agregan atributos como `products` o `warehouses` directamente dentro de Seller.

Estas relaciones serán representadas mediante asociaciones con Product y Warehouse, evitando duplicar información y manteniendo separadas las responsabilidades de cada entidad.

---

# Administrator

## Description

Administrator representa al usuario responsable de la administración de vendedores y bodegas dentro del Marketplace.

El administrador participa principalmente en la incorporación administrativa de los vendedores y en la gestión de las bodegas.

## Attributes

En el modelo actual no se identifican atributos propios adicionales para Administrator además de los heredados de User.

Las entidades administradas por el administrador se representan mediante relaciones y responsabilidades, no como listas almacenadas directamente dentro de la clase.

### Relationships

```
User
   │
   └── Administrator
         │
         ├── registers/manages ──> Seller
         │
         └── manages ────────────> Warehouse
```

### Business Rules

- El administrador es responsable de la administración de vendedores.
- El administrador es responsable de la administración de bodegas.
- Los vendedores deben ser incorporados por un administrador.
- El administrador no debe administrar información fuera de las responsabilidades asignadas a su rol.

### Responsibilities

Las principales responsabilidades de Administrator son:

- Registrar vendedores.
- Administrar vendedores.
- Registrar y administrar bodegas.
- Participar en los procesos administrativos del Marketplace.
- Facilitar la incorporación de vendedores junto con sus bodegas iniciales.

### Design Notes

No se agregan atributos como `sellers` o `warehouses` a Administrator.

El hecho de que el administrador pueda administrar estas entidades representa una responsabilidad y una relación de negocio, no necesariamente una propiedad que deba almacenarse dentro de la entidad Administrator.

---

# LogisticsOperator

## Description

LogisticsOperator representa al usuario encargado de las operaciones físicas relacionadas con las bodegas y los despachos.

Su participación se concentra en las actividades logísticas necesarias para preparar y despachar pedidos que contienen productos físicos.

## Attributes

En el modelo actual no se identifican atributos propios adicionales para LogisticsOperator además de los heredados de User.

### Relationships

```
User
   │
   └── LogisticsOperator
         │
         ├── operates ─────────> Warehouse
         │
         └── manages ──────────> Shipment
```

### Business Rules

- El operador logístico debe actuar únicamente dentro de las responsabilidades de su rol.
- Participa en las operaciones físicas de bodegas y despachos.
- Las operaciones logísticas deben respetar el estado correspondiente del pedido y del proceso de envío.
- No puede administrar información fuera de sus responsabilidades.

### Responsibilities

Las principales responsabilidades de LogisticsOperator son:

- Participar en la operación física de las bodegas.
- Participar en la preparación de pedidos.
- Participar en el despacho de productos físicos.
- Gestionar las actividades correspondientes al proceso logístico.

### Design Notes

Las actividades logísticas no se almacenan como atributos dentro de LogisticsOperator.

El operador representa al participante que ejecuta las actividades, mientras que las entidades Warehouse, Inventory, Order y Shipment representan los objetos de negocio sobre los cuales se realizan dichas actividades.

---

# Supervisor

## Description

Supervisor representa al usuario encargado de realizar actividades de consulta y seguimiento operativo dentro del Marketplace.

Su función principal es proporcionar visibilidad sobre la operación sin asumir responsabilidades administrativas que correspondan a otros roles.

## Attributes

En el modelo actual no se identifican atributos propios adicionales para Supervisor además de los heredados de User.

### Relationships

```
User
   │
   └── Supervisor
         │
         └── consults/follows ──> Business Operations
```

La relación anterior representa una responsabilidad de consulta y seguimiento, no una entidad adicional llamada `BusinessOperations`.

### Business Rules

- El supervisor posee un perfil orientado a consulta y seguimiento operativo.
- No debe modificar información que corresponda a responsabilidades de otros roles.
- No puede administrar información fuera de las responsabilidades de su rol.

### Responsibilities

Las principales responsabilidades de Supervisor son:

- Consultar información operativa.
- Realizar seguimiento de los procesos del Marketplace.
- Obtener información necesaria para supervisar la operación.
- Mantenerse dentro de las capacidades de consulta correspondientes a su rol.

### Design Notes

No se agregan listas de usuarios, vendedores, bodegas o productos como atributos de Supervisor.

La capacidad de consultar información representa una responsabilidad del rol, no una colección de entidades que deba almacenarse dentro del objeto.

---

# Product (Abstract)

## Description

Product representa un bien ofrecido dentro del Marketplace.

El catálogo diferencia entre productos físicos y productos digitales. Los productos físicos requieren inventario y despacho, mientras que los productos digitales pueden ser entregados inmediatamente después de la confirmación del pago.

Product es una clase abstracta porque representa el concepto general de producto y sus especializaciones corresponden a las diferentes modalidades de producto ofrecidas por el Marketplace.

## Attributes

| Attribute       | Type            | Description                                          |
| --------------- | --------------- | ---------------------------------------------------- |
| id              | String          | Identificador único del producto.                    |
| characteristics | String          | Características generales que describen el producto. |
| type            | ProductType     | Define si el producto es físico o digital.           |
| variants        | List\<Variant\> | Diferentes variantes disponibles del producto.       |
| status          | ProductStatus   | Estado actual del producto dentro del catálogo.      |

### Attribute Details

**id**

Identifica de forma única al producto dentro del Marketplace.

**characteristics**

Representa las características generales definidas por el vendedor para el producto.

**type**

Define la modalidad del producto:

- `PHYSICAL`
- `DIGITAL`
  **variants**

Representa diferencias del producto tales como:

- color;
- talla;
- modelo;
- otras características relevantes.
  **status**

Representa el estado actual del producto dentro del catálogo.

Los estados definidos por la especificación incluyen:

- `PUBLISHED`
- `SUSPENDED`
- `DISCONTINUED`

### Relationships

```
Product
   │
   ├── PhysicalProduct
   │       │
   │       └── requires ──> Inventory
   │
   └── DigitalProduct
```

Un producto también está relacionado con el Seller responsable de su administración.

### Business Rules

- Todo producto debe pertenecer a una de las modalidades definidas.
- Los productos físicos requieren inventario.
- Los productos físicos requieren despacho.
- Los productos digitales no requieren inventario físico.
- Los productos digitales tienen entrega inmediata después de la confirmación del pago.
- El estado del producto debe pertenecer al catálogo definido.
- Un producto puede tener cero o más variantes.

### Responsibilities

Las principales responsabilidades conceptuales de Product son:

- Representar un bien ofrecido en el Marketplace.
- Mantener las características generales del producto.
- Identificar el tipo de producto.
- Mantener sus variantes.
- Mantener su estado dentro del catálogo.

### Design Notes

Product es abstracta porque no representa por sí sola una modalidad concreta de producto.

Las diferencias operativas entre productos físicos y digitales se representan mediante PhysicalProduct y DigitalProduct.

La cantidad disponible no se almacena dentro de Product, ya que la existencia de productos físicos pertenece al dominio de Inventory.

---

# PhysicalProduct

## Description

PhysicalProduct representa un producto que existe físicamente y que requiere almacenamiento, control de inventario y despacho.

Es una especialización de Product.

## Attributes

PhysicalProduct no agrega atributos propios en el modelo actual.

La información general del producto es heredada de Product.

### Relationships

```
Product
   │
   └── PhysicalProduct
            │
            └── requires ───────> Inventory
                                      │
                                      └── stored in ──> Warehouse
```

### Business Rules

- Un producto físico requiere inventario.
- El inventario del producto físico debe estar asociado a una bodega.
- No pueden existir existencias negativas.
- No se puede reservar inventario inexistente.
- No se puede reservar inventario marcado como `DAMAGED`.

### Responsibilities

- Representar productos que requieren almacenamiento físico.
- Permitir que el producto sea gestionado mediante inventario.
- Participar en procesos de despacho.

### Design Notes

La cantidad disponible no pertenece a PhysicalProduct.

La cantidad se administra mediante Inventory, debido a que un mismo producto puede tener existencias distribuidas en diferentes bodegas.

---

# DigitalProduct

## Description

DigitalProduct representa un producto que no requiere almacenamiento físico ni despacho.

Es una especialización de Product.

## Attributes

DigitalProduct no agrega atributos propios en el modelo actual.

La información general del producto es heredada de Product.

### Relationships

```
Product
   │
   └── DigitalProduct
            │
            └── delivered after ──> PaymentConfirmation
```

La entidad PaymentConfirmation representa aquí un concepto del flujo de negocio y no se incorpora todavía como una nueva entidad del modelo.

### Business Rules

- Los productos digitales no requieren inventario físico.
- Los productos digitales no requieren despacho.
- La entrega se realiza inmediatamente después de la confirmación del pago.

### Responsibilities

- Representar productos digitales ofrecidos en el Marketplace.
- Diferenciar los productos que no requieren almacenamiento ni logística física.
- Participar en el proceso de entrega digital.

### Design Notes

No se agrega una cantidad de inventario físico a DigitalProduct.

La ausencia de inventario físico es una diferencia fundamental respecto a PhysicalProduct.

---

# Warehouse

## Description

Warehouse representa un espacio físico destinado al almacenamiento y administración de inventario.

El sistema distingue entre bodegas pertenecientes al Marketplace y bodegas pertenecientes a vendedores.

## Attributes

| Attribute | Type          | Description                                                            |
| --------- | ------------- | ---------------------------------------------------------------------- |
| id        | String        | Identificador único de la bodega.                                      |
| location  | String        | Ubicación física de la bodega.                                         |
| type      | WarehouseType | Clasifica la bodega como perteneciente al Marketplace o a un vendedor. |

### Relationships

```
Seller
   │
   └── owns ───────────────> Warehouse
                                │
                                └── contains ───────> Inventory
                                                        │
                                                        └── references ──> PhysicalProduct
```

Una bodega puede estar asociada con múltiples registros de inventario,
correspondientes a diferentes productos almacenados en ella.

### Business Rules

- Las bodegas deben tener una ubicación.
- Las bodegas deben pertenecer a una clasificación válida.
- Se distinguen bodegas del Marketplace y bodegas de vendedores.
- El inventario físico debe estar asociado a una bodega específica.

### Responsibilities

- Representar los espacios físicos de almacenamiento.
- Permitir la organización del inventario físico.
- Identificar el lugar donde se encuentran las existencias de productos.

### Design Notes

La cantidad de productos almacenados no se representa directamente mediante un atributo de Warehouse.

La relación se realiza mediante Inventory, ya que el inventario debe estar vinculado obligatoriamente a un producto y a una bodega específica.

---

# Inventory

## Description

`Inventory` representa las existencias de un producto físico dentro de una
bodega específica.

El inventario es distribuido dentro del Marketplace y cada registro de
inventario debe estar vinculado obligatoriamente a un producto y a una bodega
específica.

Esta entidad permite controlar la cantidad disponible de cada producto físico
en cada ubicación de almacenamiento.

---

## Attributes

| Attribute  | Type              | Description                                                 |
| ---------- | ----------------- | ----------------------------------------------------------- |
| `id`       | `String`          | Identificador único del registro de inventario.             |
| `quantity` | `int`             | Cantidad de unidades disponibles del producto en la bodega. |
| `status`   | `InventoryStatus` | Estado operativo del inventario.                            |

---

## Attribute Details

### id

Identifica de forma única un registro de inventario.

### quantity

Representa la cantidad de unidades disponibles del producto asociado dentro de
la bodega correspondiente.

**Restrictions:**

- No puede ser negativa.
- La cantidad debe actualizarse de acuerdo con los movimientos de inventario.

### status

Representa el estado operativo del inventario.

Entre los estados contemplados por las reglas de negocio se encuentra el estado
`DAMAGED`, utilizado para identificar inventario que no puede ser reservado.

No se define todavía un catálogo completo de valores para este atributo porque
la especificación solamente establece explícitamente la existencia de estados
como el inventario dañado.

---

### Relationships

```text
PhysicalProduct
   │
   └── has ──────────────────> Inventory
                                  │
                                  └── stored in ──────────> Warehouse
```

Cada registro de Inventory está asociado obligatoriamente con:

- un PhysicalProduct;
- una Warehouse.
  Un producto físico puede tener múltiples registros de inventario cuando sus existencias se encuentran distribuidas entre diferentes bodegas.

Una bodega puede contener inventario correspondiente a múltiples productos.

### Business Rules

- Cada registro de inventario debe estar vinculado a un producto.
- Cada registro de inventario debe estar vinculado a una bodega específica.
- No se permiten existencias negativas bajo ninguna circunstancia.
- No se puede reservar inventario inexistente.
- No se puede reservar inventario marcado como `DAMAGED`.
- El inventario puede experimentar movimientos de ingreso, reserva, salida por venta, ajuste y devolución.

### Inventory Movements

Los movimientos de inventario representan las operaciones que modifican las existencias disponibles.

Los movimientos definidos por la especificación son:

| Movement      | Description                                                       |
| ------------- | ----------------------------------------------------------------- |
| `INCOME`      | Registra el ingreso de nuevas existencias.                        |
| `RESERVATION` | Reserva existencias para un proceso de compra.                    |
| `SALE_EXIT`   | Registra la salida de existencias debido a una venta.             |
| `ADJUSTMENT`  | Modifica las existencias para corregir diferencias de inventario. |
| `RETURN`      | Registra el ingreso de unidades debido a una devolución.          |

Estos movimientos se consideran operaciones del dominio de Inventory y no entidades independientes dentro del modelo actual.

### Responsibilities

Las principales responsabilidades de Inventory son:

- Mantener la cantidad disponible de un producto físico.
- Asociar las existencias con una bodega específica.
- Controlar que las existencias nunca sean negativas.
- Permitir la gestión de los movimientos de inventario.
- Impedir la reserva de inventario inexistente o dañado.
- Mantener el estado operativo de las existencias.

### Design Notes

Inventory funciona como entidad intermedia entre PhysicalProduct y Warehouse.

No se coloca la cantidad directamente en PhysicalProduct, porque un mismo producto puede tener existencias distribuidas en diferentes bodegas.

Tampoco se coloca una lista de productos directamente en Warehouse, ya que la relación entre producto, cantidad y ubicación se representa mediante Inventory.

Los movimientos de inventario se modelan inicialmente como operaciones de Inventory y no como una entidad independiente.

---

# Cart

## Description

`Cart` representa la selección provisional de productos realizada por un comprador antes de confirmar un pedido.

El carrito forma parte del proceso de compra y permite al comprador agregar, modificar o eliminar productos antes de confirmar la compra.

## Attributes

| Attribute | Type     | Description                      |
| --------- | -------- | -------------------------------- |
| `id`      | `String` | Identificador único del carrito. |

La información del comprador y los productos contenidos se representan mediante relaciones con otras entidades del dominio.

### Relationships

```text
Buyer
   │
   └── owns ─────────────────> Cart
                                  │
                                  └── contains ───────> CartItem
                                                           │
                                                           └── references ──> Product
```

Un comprador puede tener un carrito activo.

Un carrito puede contener cero o múltiples CartItem.

Cada CartItem referencia un único producto.

### Business Rules

- El carrito pertenece a un comprador.
- El carrito representa una selección provisional.
- El comprador puede agregar productos al carrito.
- El comprador puede modificar las cantidades seleccionadas.
- El comprador puede eliminar productos del carrito.
- Un carrito puede encontrarse vacío.
- La existencia de un producto en el carrito no garantiza que el producto esté disponible al momento de confirmar el pedido.

### Responsibilities

Las principales responsabilidades de Cart son:

- Mantener la selección provisional realizada por el comprador.
- Administrar los elementos seleccionados.
- Permitir la modificación de cantidades.
- Permitir la eliminación de productos.
- Servir como base para la creación del pedido.

### Design Notes

Cart no almacena directamente una lista de productos.

Los productos seleccionados se representan mediante CartItem, lo que permite mantener la cantidad de cada producto seleccionado.

El carrito no representa una compra definitiva. Una vez que el comprador confirma la compra, el contenido relevante se transforma en elementos del pedido.

---

# CartItem

## Description

`CartItem` representa una línea individual dentro de un carrito de compras.

Permite asociar un producto con la cantidad que el comprador desea adquirir.

Esta entidad evita almacenar directamente una lista simple de productos dentro de `Cart` y permite representar correctamente cantidades diferentes para cada producto.

## Attributes

| Attribute  | Type     | Description                                          |
| ---------- | -------- | ---------------------------------------------------- |
| `id`       | `String` | Identificador único del elemento del carrito.        |
| `quantity` | `int`    | Cantidad del producto seleccionada por el comprador. |

### Relationships

```text
Cart
   │
   └── contains ─────────────> CartItem
                                  │
                                  └── references ───────> Product
```

Cada CartItem pertenece a un único Cart y referencia un único Product.

Un Cart puede contener múltiples CartItem.

### Business Rules

- Cada elemento del carrito debe referenciar un producto.
- La cantidad seleccionada debe ser válida para la operación de compra.
- Un producto puede ser representado por un elemento dentro de un carrito.
- Los elementos del carrito pueden modificarse mientras la compra no haya sido confirmada.

### Responsibilities

- Representar un producto seleccionado dentro de un carrito.
- Mantener la cantidad seleccionada.
- Asociar el producto con el carrito correspondiente.

### Design Notes

CartItem mantiene una relación de composición con Cart.

Si el carrito deja de existir, sus elementos dejan de tener sentido dentro del dominio.

La cantidad no pertenece al Product, ya que representa una selección específica realizada por un comprador.

---

# Order

## Description

`Order` representa el compromiso comercial formal generado después de que el comprador confirma su compra.

El pedido constituye el proceso central del ciclo comercial de NexusMarket y mantiene el resultado de la compra realizada por el comprador.

A diferencia de `Cart`, el `Order` representa una operación comercial formalizada.

## Attributes

| Attribute | Type          | Description                                 |
| --------- | ------------- | ------------------------------------------- |
| `id`      | `String`      | Identificador único del pedido.             |
| `status`  | `OrderStatus` | Estado actual del ciclo de vida del pedido. |

### Relationships

```text
Buyer
   │
   └── creates ──────────────> Order
                                  │
                                  ├── contains ───────> OrderItem
                                  │
                                  ├── generates ──────> Shipment
                                  │
                                  ├── generates ──────> Invoice
                                  │
                                  └── may generate ───> Return
```

Un comprador puede generar múltiples pedidos.

Un pedido contiene uno o más elementos de pedido.

Un pedido puede generar un envío cuando contiene productos físicos.

Un pedido puede generar una factura.

Un pedido puede posteriormente generar una devolución.

### Order Lifecycle

El ciclo de vida definido para los pedidos es:

```text
Cart
  │
  ▼
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

Los estados representan las principales etapas del proceso comercial:

| State                   | Description                                                         |
| ----------------------- | ------------------------------------------------------------------- |
| `CART`                  | Selección provisional de productos.                                 |
| `PENDING_PAYMENT`       | El pedido espera la confirmación financiera.                        |
| `PAID`                  | El pago fue confirmado y puede comenzar el proceso de alistamiento. |
| `DISPATCHED`            | Los productos físicos han salido de la bodega.                      |
| `DELIVERED / FINALIZED` | La entrega fue confirmada y el pedido concluyó satisfactoriamente.  |

### Business Rules

- El pedido representa el compromiso comercial formal.
- Un pedido se genera a partir de la compra confirmada por el comprador.
- El pedido debe mantener los productos que fueron efectivamente comprados.
- Un pedido finalizado no puede ser modificado bajo ninguna circunstancia.
- Los productos físicos requieren procesos de alistamiento y despacho.
- Los productos digitales pueden ser entregados inmediatamente después de la confirmación del pago.
- El estado del pedido debe seguir el ciclo de vida definido por el sistema.

### Responsibilities

Las principales responsabilidades de Order son:

- Representar una compra formalizada.
- Mantener el estado del proceso comercial.
- Mantener los elementos que fueron comprados.
- Servir como referencia para facturación.
- Servir como referencia para logística.
- Servir como referencia para devoluciones.

### Design Notes

Order no mantiene una referencia al Cart como su contenido permanente.

El carrito representa una selección provisional, mientras que el pedido representa el resultado de la compra confirmada.

Por esta razón, los productos que fueron eliminados del carrito antes de la confirmación no forman parte del pedido.

Los productos comprados se representan mediante OrderItem.

---

# OrderItem

## Description

`OrderItem` representa una línea individual dentro de un pedido confirmado.

Cada elemento identifica un producto que fue efectivamente adquirido y la cantidad correspondiente.

A diferencia de `CartItem`, un `OrderItem` forma parte de una compra formalizada y debe conservar la información necesaria para representar lo que fue comprado.

## Attributes

| Attribute  | Type     | Description                                             |
| ---------- | -------- | ------------------------------------------------------- |
| `id`       | `String` | Identificador único del elemento del pedido.            |
| `quantity` | `int`    | Cantidad del producto adquirida.                        |
| `price`    | `Money`  | Precio correspondiente al producto dentro de la compra. |

### Relationships

```text
Order
   │
   └── contains ─────────────> OrderItem
                                  │
                                  └── references ───────> Product
```

Cada OrderItem pertenece a un único Order y referencia un único Product.

Un pedido contiene uno o más elementos de pedido.

### Business Rules

- Cada elemento del pedido debe referenciar un producto.
- La cantidad comprada debe ser válida.
- El elemento representa únicamente productos que fueron efectivamente incluidos en la compra confirmada.
- Los elementos de un pedido finalizado no pueden modificarse.
- El precio registrado corresponde al valor utilizado para representar la compra.

### Responsibilities

- Representar un producto adquirido.
- Mantener la cantidad comprada.
- Mantener el precio correspondiente a la compra.
- Formar parte del contenido permanente de un pedido.

### Design Notes

OrderItem se mantiene separado de CartItem porque representan dos etapas diferentes del proceso de compra.

CartItem representa una selección provisional que puede cambiar.

OrderItem representa una compra confirmada y debe conservarse como parte del historial del pedido.

La separación permite que los productos eliminados del carrito antes de la confirmación no aparezcan posteriormente en el pedido.

---

# Shipment

## Description

`Shipment` representa el proceso logístico utilizado para transportar productos físicos desde una bodega hasta el destinatario correspondiente.

El envío forma parte del ciclo logístico del pedido y comprende las actividades relacionadas con preparación, despacho, transporte y entrega.

Los productos digitales no requieren un `Shipment`, debido a que su entrega se realiza de forma inmediata después de la confirmación del pago.

## Attributes

| Attribute   | Type             | Description                                     |
| ----------- | ---------------- | ----------------------------------------------- |
| `id`        | `String`         | Identificador único del envío.                  |
| `recipient` | `Address`        | Destinatario y ubicación asociada a la entrega. |
| `status`    | `ShipmentStatus` | Estado actual del proceso de envío.             |

### Relationships

```text
Order
   │
   └── generates ────────────> Shipment
                                  │
                                  └── delivered to ────> Address
```

Un pedido puede generar un envío cuando requiere entrega física.

Un pedido compuesto exclusivamente por productos digitales no requiere un envío físico.

### Business Rules

- Los productos físicos requieren despacho.
- Los productos digitales no requieren despacho físico.
- El envío forma parte del proceso logístico posterior al pago.
- El envío debe estar asociado con un pedido.
- El proceso logístico comprende preparación, despacho, transporte y entrega.
- La entrega debe realizarse al destinatario correspondiente.

### Responsibilities

Las principales responsabilidades de Shipment son:

- Representar el proceso de envío de un pedido.
- Identificar el destinatario de la entrega.
- Mantener el estado del proceso logístico.
- Permitir el seguimiento de la entrega física.
- Representar la finalización del proceso de entrega.

### Design Notes

Shipment se relaciona con Order y no con Cart, porque el envío solamente existe como consecuencia de una compra formalizada.

No todos los pedidos requieren un envío físico. Los pedidos que contienen productos digitales pueden completar su entrega sin generar un Shipment.

La gestión de los estados concretos del envío se definirá posteriormente, manteniendo únicamente los estados que puedan justificarse mediante las reglas de negocio de la especificación.

---

# Invoice

## Description

`Invoice` representa la información comercial asociada a una venta realizada
dentro del Marketplace.

La facturación forma parte del proceso comercial y está asociada al pedido que
origina la venta.

---

## Attributes

| Attribute | Type     | Description                        |
| --------- | -------- | ---------------------------------- |
| `id`      | `String` | Identificador único de la factura. |

No se agregan otros atributos específicos en el modelo actual debido a que la
especificación funcional no define una estructura detallada para la factura.

---

### Relationships

```text
Order
   │
   └── generates ────────────> Invoice
```

Una factura se genera como consecuencia de un pedido.

La factura debe mantener la relación con el pedido al que corresponde.

### Business Rules

- La facturación forma parte de los procesos incluidos en el sistema.
- La factura debe estar asociada a una venta.
- La información de facturación debe corresponder al pedido relacionado.
- Una vez generada, la información debe conservar la trazabilidad de la venta correspondiente.

### Responsibilities

Las principales responsabilidades de Invoice son:

- Representar la información comercial asociada a una venta.
- Identificar la factura correspondiente a un pedido.
- Mantener la trazabilidad entre la venta y su información de facturación.

### Design Notes

La especificación identifica la facturación como un componente principal del Marketplace, pero no define en detalle los campos que debe contener una factura.

Por esta razón, el modelo no agrega atributos como impuestos, subtotal, descuentos o fecha de emisión hasta que exista una regla de negocio que justifique su inclusión.

---

# Return

## Description

`Return` representa el proceso mediante el cual una compra puede entrar en un flujo de devolución después de haber sido realizada.

Las devoluciones forman parte de los procesos de posventa incluidos dentro del alcance del Marketplace.

## Attributes

| Attribute | Type     | Description                           |
| --------- | -------- | ------------------------------------- |
| `id`      | `String` | Identificador único de la devolución. |

La especificación no proporciona actualmente atributos adicionales para describir una devolución.

### Relationships

```text
Order
   │
   └── may generate ────────> Return
                                  │
                                  └── may generate ──> Refund
```

Una devolución está asociada con un pedido previamente realizado.

Una devolución puede dar lugar a un reembolso.

### Business Rules

- Las devoluciones forman parte de los procesos incluidos en el sistema.
- Una devolución debe estar relacionada con una compra existente.
- El proceso de devolución debe mantener la trazabilidad del pedido original.
- La devolución puede estar relacionada con un proceso de reembolso.
- Las reglas específicas de aceptación de devoluciones no se encuentran detalladas en la especificación funcional.

### Responsibilities

Las principales responsabilidades de Return son:

- Representar una solicitud o proceso de devolución.
- Mantener la relación con el pedido original.
- Permitir la trazabilidad del proceso de posventa.
- Servir como referencia para un posible reembolso.

### Design Notes

La especificación confirma que las devoluciones forman parte del alcance del sistema, pero no define un conjunto detallado de atributos, estados o condiciones de aceptación.

Por esta razón, el modelo mantiene únicamente la información necesaria para representar el concepto y su relación con el pedido.

Las reglas específicas de devolución deberán definirse posteriormente si son necesarias para la implementación.

---

# Refund

## Description

`Refund` representa el proceso mediante el cual se realiza un reembolso relacionado con una devolución.

Los reembolsos forman parte de los procesos de posventa contemplados dentro del alcance del Marketplace.

## Attributes

| Attribute | Type     | Description                        |
| --------- | -------- | ---------------------------------- |
| `id`      | `String` | Identificador único del reembolso. |

La especificación funcional no define actualmente atributos adicionales para el reembolso.

### Relationships

```text
Return
   │
   └── may generate ─────────> Refund
```

Un Refund está relacionado con una devolución.

El reembolso permite mantener la trazabilidad entre el proceso de devolución y la compensación correspondiente.

### Business Rules

- Los reembolsos forman parte de los procesos incluidos en el sistema.
- Un reembolso debe estar relacionado con un proceso de devolución.
- El reembolso debe mantener la trazabilidad de la compra original.
- Las reglas específicas sobre montos, métodos y condiciones del reembolso no se encuentran definidas en la especificación funcional.

### Responsibilities

Las principales responsabilidades de Refund son:

- Representar un proceso de reembolso.
- Mantener la relación con la devolución correspondiente.
- Permitir la trazabilidad del proceso de posventa.

### Design Notes

La especificación identifica los reembolsos como parte del alcance del sistema, pero no define su estructura interna ni las condiciones detalladas bajo las cuales debe ejecutarse.

Por esta razón, no se agregan atributos como `amount`, `paymentMethod` o `date` hasta contar con una regla de negocio que justifique su existencia.

```

```
