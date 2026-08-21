# Diagrama del sistema

Conversión del diagrama de draw.io a **Markdown + Mermaid**. Se conservan los nombres,
atributos, herencias y relaciones que aparecen explícitamente en el archivo original.
Las preguntas/comentarios del diagrama se mantienen como observaciones y no se
resuelven automáticamente. fileciteturn1file0

## Diagrama de clases

```mermaid
classDiagram
direction TB

class Persona {
    <<abstract>>
    ID
    Nombre
    Correo
    Rol
    Estado
}

class Usuario {
    ID
    Nombre
    Correo
}

class Vendedor {
    ID
    Nombre
    Bodega
}

class OP_Logistico {
    ID
    Nombre
    Bodegas_F
}

class Administrador {
    ID
    Nombre
    Vendedores
    Bodegas_V
    Bodegas_F
}

class Supervisor {
    ID
    Nombre
    Usuarios
    Bodegas_V
    Bodegas_F
}

class Carrito {
    ID
    Usuario
    Productos
}

class Bodegas {
    ID
    Ubicacion
    Inventario
}

class Inventario {
    LugarBodega
    Productos
}

class Producto {
    <<abstract>>
    ID
    Caracteristicas
    Estado
}

class Pedido {
    ID
    Comprador
    Carrito
}

class Envios {
    ID
    Destinatario
    Carrito
}

class Producto_Fisico {
    ID
    Cantidad
    Tipo
}

class Producto_Virtual {
    ID
    Cantidad
    Tipo
}

Persona <|-- Supervisor
Persona <|-- Vendedor
Persona <|-- OP_Logistico
Persona <|-- Administrador
Persona <|-- Usuario

Producto_Fisico --> Producto : Caracteristicas
Producto_Virtual --> Producto : Caracteristicas

Pedido --> Envios : relacion indicada
```

## Estructura conceptual del diagrama original

```mermaid
flowchart TB
    PersonaA["Persona<br/>(Abstracta)"]

    SupervisorA["Supervisor"]
    UsuarioA["Usuarios / compradores"]
    VendedorA["Vendedores"]
    AdministradorA["Administrador"]
    OperadorA["Operador logistico"]

    PersonaA --> SupervisorA
    PersonaA --> UsuarioA
    PersonaA --> VendedorA
    PersonaA --> AdministradorA
    PersonaA --> OperadorA

    BodegasA["Bodegas"]
    Catalogo["Catalogo"]
    ProductosA["Productos<br/>(Abstracta)"]
    CarritoA["Carrito de compras"]
    PedidosA["Pedidos"]
    Venta["Venta"]
    EnviosA["Envios"]
    InventarioA["Inventario"]
    Reporte["Reporte"]
    Facturacion["Facturacion"]

    PedidosA --> EnviosA
```

## Observaciones incluidas en el diagrama

- **Persona:** el diagrama plantea si conviene crear una clase `Persona` para
  compartir atributos entre los distintos tipos de usuarios y permitir que cada
  uno tenga sus atributos propios.
- **Métodos de pago:** se pregunta si debería existir una clase separada para
  manejar los métodos de pago.
- **Pedidos y envíos:** se plantea si los pedidos y los envíos podrían
  simplificarse en una misma clase.
- **Productos:** `Producto` aparece como clase abstracta y se distinguen
  `Producto fisico` y `Producto virtual`.

## Clases y atributos presentes

| Clase                  | Atributos                                              |
| ---------------------- | ------------------------------------------------------ |
| `Persona` (abstracta)  | `ID`, `Nombre`, `Correo`, `Rol`, `Estado`              |
| `Usuario`              | `ID`, `Nombre`, `Correo`                               |
| `Vendedor`             | `ID`, `Nombre`, `Bodega`                               |
| `OP logistico`         | `ID`, `Nombre`, `Bodegas F`                            |
| `Administrador`        | `ID`, `Nombre`, `Vendedores`, `Bodegas V`, `Bodegas F` |
| `Supervisor`           | `ID`, `Nombre`, `Usuarios`, `Bodegas V`, `Bodegas F`   |
| `Carrito`              | `ID`, `Usuario`, `Productos`                           |
| `Bodegas`              | `ID`, `Ubicacion`, `Inventario`                        |
| `Inventario`           | `LugarBodega`, `Productos`                             |
| `Producto` (abstracta) | `ID`, `Caracteristicas`, `Estado`                      |
| `Pedido`               | `ID`, `Comprador`, `Carrito`                           |
| `Envios`               | `ID`, `Destinatario`, `Carrito`                        |
| `Producto fisico`      | `ID`, `Cantidad`, `Tipo`                               |
| `Producto virtual`     | `ID`, `Cantidad`, `Tipo`                               |

## Elementos conceptuales adicionales

El diagrama también contiene los elementos `Catalogo`, `Venta`, `Reporte`,
`Inventario`, `Facturacion`, `Bodegas`, `Carrito de compras` y
`Productos (Abstracta)` como parte de la visión general del sistema.
