# Output Ports

Los Output Ports representan las interfaces mediante las cuales el sistema
interactúa con componentes externos.

Estos puertos permiten que las reglas del dominio y de la aplicación no
dependan directamente de tecnologías concretas de persistencia, pagos,
logística u otros servicios externos.

Las implementaciones concretas de estos puertos pertenecen a las capas
externas de la arquitectura.

---

# UserRepository

## Description

`UserRepository` define las operaciones necesarias para consultar y almacenar
la información de los usuarios del Marketplace.

El puerto abstrae la persistencia de usuarios para evitar que las capas de
dominio y aplicación dependan directamente de una tecnología de base de datos.

---

## Responsibilities

- Buscar usuarios.
- Registrar usuarios.
- Actualizar información de usuarios.
- Consultar usuarios según los criterios necesarios para las operaciones del
  sistema.

---

## Design Notes

`UserRepository` es una interfaz.

La implementación concreta del repositorio pertenece a una capa externa y no
debe formar parte del dominio.

---

# ProductRepository

## Description

`ProductRepository` define las operaciones necesarias para consultar y
persistir los productos registrados dentro del Marketplace.

---

## Responsibilities

- Buscar productos.
- Registrar productos.
- Actualizar productos.
- Consultar productos disponibles para las operaciones del catálogo.

---

## Design Notes

El repositorio permite que el dominio trabaje con productos sin depender de
una implementación concreta de persistencia.

---

# OrderRepository

## Description

`OrderRepository` define las operaciones necesarias para almacenar y consultar
los pedidos generados por los compradores.

---

## Responsibilities

- Registrar pedidos.
- Buscar pedidos.
- Consultar el estado de un pedido.
- Actualizar la información necesaria durante el ciclo de vida del pedido.

---

## Design Notes

`OrderRepository` mantiene abstraída la persistencia de los pedidos.

La implementación concreta puede utilizar una base de datos u otro mecanismo
de almacenamiento sin afectar al dominio.

---

# InventoryRepository

## Description

`InventoryRepository` define las operaciones necesarias para consultar y
persistir la información relacionada con el inventario.

---

## Responsibilities

- Buscar registros de inventario.
- Registrar inventario.
- Actualizar existencias.
- Consultar la disponibilidad de productos físicos.
- Persistir los cambios producidos por las operaciones de inventario.

---

## Design Notes

El puerto permite que `Inventory` y los servicios de dominio relacionados
trabajen con existencias sin conocer la tecnología utilizada para almacenarlas.

---

# PaymentPort

## Description

`PaymentPort` define la interfaz mediante la cual el sistema puede solicitar y
verificar operaciones de pago.

El procesamiento real del pago pertenece a un componente externo.

---

## Responsibilities

- Solicitar el procesamiento de un pago.
- Verificar el resultado de una operación de pago.
- Comunicar el resultado de la operación al sistema.

---

## Design Notes

El dominio no debe depender directamente de una plataforma, proveedor o
tecnología específica de pagos.

Una implementación concreta de `PaymentPort` puede comunicarse con el
proveedor externo correspondiente.

---

# ShipmentPort

## Description

`ShipmentPort` define la interfaz utilizada para interactuar con los servicios
externos relacionados con el despacho y seguimiento de envíos.

---

## Responsibilities

- Solicitar la creación de un envío.
- Consultar información relacionada con el envío.
- Obtener el estado del proceso de entrega.

---

## Design Notes

`ShipmentPort` permite separar la lógica del Marketplace de los proveedores
externos de transporte o logística.

El dominio trabaja con la interfaz y no con una empresa de transporte
específica.

---

# NotificationPort

## Description

`NotificationPort` define la interfaz utilizada para enviar comunicaciones
relacionadas con las operaciones del Marketplace.

---

## Responsibilities

- Enviar notificaciones al usuario.
- Comunicar eventos relevantes del proceso de compra.
- Comunicar cambios importantes relacionados con pedidos o envíos.

---

## Design Notes

La implementación concreta del puerto puede utilizar diferentes mecanismos de
comunicación.

El dominio no debe depender directamente de un proveedor específico de correo
electrónico, mensajes u otro canal de comunicación.

---

# General Design Principle

Los Output Ports deben representar necesidades externas del sistema sin
introducir detalles tecnológicos dentro del dominio.

Las interfaces pertenecen al lado interno de la arquitectura, mientras que sus
implementaciones concretas pertenecen a las capas externas.

```text
Domain / Application
        │
        │ depends on
        ▼
   Output Port
        ▲
        │ implements
        │
Infrastructure
        │
        ├── Database
        ├── Payment Provider
        ├── Shipping Provider
        └── Notification Provider
```
