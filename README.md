# NexusMarket

Marketplace con dos almacenes de datos: **MySQL** para la informacion relacional
(usuarios, vendedores, productos, pedidos, envios, devoluciones, reembolsos) y
**MongoDB** para los documentos de carrito, inventario y seguimiento de envios.

La implementacion vive en [`demo/`](demo) y sigue el SDD de [`SDD/Domain`](SDD/Domain):
`Domain Model.md`, `Domain Services.md`, `Domain Value Objects.md` y `Outputs Ports.md`.

## Stack

- Java 17+ (probado con Java 21) y Spring Boot 3.2.5
- Spring Web, Spring Security (JWT stateless), Spring Data JPA (MySQL), Spring Data MongoDB
- Hibernate con `open-in-view=false`; las consultas que alimentan la API cargan de forma
  explicita las relaciones (`@EntityGraph` / `JOIN FETCH`)
- Bean Validation en los DTO y un `GlobalExceptionHandler` comun
  (400 validacion, 401 credenciales, 403 permisos, 404 no encontrado, 405, 409 regla de negocio/ integridad, 500)
- Todos los identificadores de entidades y de los documentos de Mongo son `Long`
  (los identificadores de los documentos de Mongo son sus claves internas)

## Requisitos

- MySQL en `localhost:3306` (base `nexusmarket`, se crea si no existe)
- MongoDB en `localhost:27017` (base `nexusmarket_mongo`)
- Maven Wrapper incluido (no hace falta Maven instalado)

Configuracion en [`demo/src/main/resources/application.properties`](demo/src/main/resources/application.properties).
Con Docker:

```bash
docker run -d --name nexus-mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=nexusmarket mysql:8

docker run -d --name nexus-mongo -p 27017:27017 mongo:7
```

## Ejecutar

```bash
cd demo
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

El perfil `dev` siembra usuarios de prueba (solo si la base esta vacia):

| Rol | Email | Contrasena |
| --- | --- | --- |
| Administrador | `admin@nexusmarket.com` | `admin123` |
| Comprador | `buyer@nexusmarket.com` | `buyer123` |
| Vendedor | `seller@nexusmarket.com` | `seller123` |

Salud del servicio: `GET /api/health`.

## Autenticacion y roles

1. `POST /api/auth/register` solo crea cuentas **BUYER** (el auto-registro de
   vendedores esta prohibido por el SDD).
2. `POST /api/auth/login` devuelve un JWT; se envia como `Authorization: Bearer <token>`.
3. Un administrador incorpora vendedores con `POST /api/sellers` y compradores
   adicionales con `POST /api/buyers`.
4. Un comprador bloqueado no puede usar carrito, pagos ni pedidos.

## API (resumen)

| Modulo | Endpoints principales |
| --- | --- |
| Usuarios | `GET/PUT /api/users/{id}`, `PUT /api/users/{id}/block`, `/activate` |
| Compradores | `POST /api/buyers` (ADMIN), `GET /api/buyers`, `GET/PUT /api/buyers/{id}` |
| Vendedores | `POST /api/sellers` (ADMIN), `PUT/POST /api/sellers/{id}/block`, `/activate` |
| Bodegas | `GET/POST /api/warehouses`, `PUT/DELETE /api/warehouses/{id}` |
| Productos | `GET/POST /api/products`, `PUT/DELETE /api/products/{id}`, `POST /api/products/{id}/publish`, `/suspend`, `/discontinue`, variantes en `/{id}/variants` |
| Inventario | `POST /api/inventory/stock/add`, `/remove`, `/adjust`, `/return`, `/damaged`, `POST /api/inventory/stock/release` |
| Carrito | `GET /api/cart`, `POST /api/cart/items`, `PUT/DELETE /api/cart/items/{id}` |
| Pedidos | `POST/GET /api/orders`, `GET /api/orders/{id}`, `POST /api/orders/{id}/pay`, `/dispatch`, `/confirm-delivery`, `/finalize`, `/cancel`, `DELETE /api/orders/{id}` (ADMIN), `GET /api/orders/{id}/invoice` |
| Envios | `POST/GET /api/shipments/order/{orderId}`, `GET /api/shipments/{id}` |
| Devoluciones | `POST /api/returns`, `POST /api/returns/{id}/approve`, `POST /api/returns/{id}/receive?warehouseId=&damagedQuantity=` |
| Reembolsos | `POST /api/refunds/return/{returnId}` (ADMIN) |
| Reportes | `GET /api/reports/admin` (ADMIN) |

## Reglas de negocio destacadas

- **Productos**: se registran como borrador (`SUSPENDED`) y se publican con
  `PublishProduct`. `ProductStatus = {PUBLISHED, SUSPENDED, DISCONTINUED}`.
  Los productos digitales (`productType=DIGITAL`) exigen `downloadUrl` y no
  pasan por inventario ni envio fisico.
- **Inventario**: `available = quantity - reserved - damaged`. `ReserveStock`
  ocurre al crear el pedido; `RemoveStock (SALE_EXIT)` consume la reserva al
  despachar; `ReturnStock` solo reincorpora unidades recibidas y aptas.
  `MarkDamaged` retira disponibilidad sin alterar el total fisico.
- **Pedidos**: `CreateOrder` valida, reserva inventario y vacia el carrito.
  Ciclo fisico: `PENDING_PAYMENT -> PAID -> DISPATCHED -> DELIVERED -> FINALIZED`.
  Pedido 100% digital: `PAID -> DELIVERED` inmediato (nunca `DISPATCHED`).
  `PENDING_PAYMENT -> CANCELLED` libera la reserva (manual o por expiracion a las
  24 h, configurable). Solo un pedido `CANCELLED` puede borrarse.
- **Devoluciones/reembolsos**: `RequestReturn -> APPROVED -> (ProcessRefund |
  ReceiveReturnUnits)`. Aprobar no toca inventario, reembolsar tampoco; la
  recepcion reincorpora solo las unidades aptas y registra las dañadas.
  El reembolso exige una devolucion aprobada (o ya recibida).
- **Trazabilidad**: un producto con pedidos o una bodega con inventario no se
  eliminan (se descontinuan/desactivan); un pedido con efectos no se borra.
- **Notificaciones**: se emiten a traves de `NotificationPort` (bloqueo de
  usuario, eventos de envio, devoluciones).
- **FinalizeOrder**: scheduler configurable (`nexusmarket.order-finalization-*`).

## Tests

```bash
cd demo
./mvnw test
```

- 57 tests de dominio (`application.domain.models.*`).
- 8 tests de humo end-to-end (`application.smoke.MarketplaceSmokeE2ETest`):
  arranca la aplicacion real con **H2 en modo MySQL** y **Mongo Java Server
  in-memory**, y recorre registro/login, alta de vendedor por admin, bodega +
  producto + stock, carrito, pedido con reserva y limpieza de carrito, pago,
  envio/despacho/entrega, factura, devolucion, reembolso, recepcion con
  reintegro, pedido digital, cancelacion, inventario dañado, borrado protegido
  y reporte administrativo. No requiere Docker ni bases de datos locales.
