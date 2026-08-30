# Domain Value Objects

Los Value Objects representan conceptos del dominio definidos por sus valores y
no por una identidad independiente.

A diferencia de las entidades, los Value Objects no necesitan un identificador
propio para distinguir una instancia de otra.

En NexusMarket se identifican los siguientes Value Objects:

- `Address`
- `Money`
- `Variant`

---

# Address

## Description

`Address` representa una ubicación utilizada dentro del Marketplace para
identificar un lugar de entrega.

Este concepto se utiliza principalmente en la información específica del
comprador, donde existe una dirección principal y pueden existir direcciones
adicionales.

---

## Attributes

| Attribute | Type     | Description                                            |
| --------- | -------- | ------------------------------------------------------ |
| `value`   | `String` | Representación de la ubicación o dirección de entrega. |

---

## Usage

`Address` es utilizado por `Buyer` para representar:

- La dirección principal de entrega.
- Las direcciones adicionales de entrega.

```text
Buyer
   │
   ├── primaryAddress ────────> Address
   │
   └── additionalAddresses ──> Address
```

La especificación establece que la dirección principal es obligatoria y que las direcciones adicionales son opcionales.

### Business Rules

- La dirección principal del comprador es obligatoria.
- Las direcciones adicionales son opcionales.
- Una dirección debe representar una ubicación válida para realizar una entrega.
- Address no necesita un identificador propio dentro del dominio.

### Responsibilities

Las principales responsabilidades de Address son:

- Representar una ubicación de entrega.
- Proporcionar un valor reutilizable para las diferentes direcciones asociadas a un comprador.

### Design Notes

Address se modela como Value Object porque su importancia dentro del dominio corresponde al valor de la ubicación y no a una identidad independiente.

La especificación no define los componentes internos de una dirección, por lo que no se agregan atributos como ciudad, departamento, código postal o número de calle hasta que exista una regla que los justifique.

---

# Money

## Description

Money representa un valor monetario utilizado para expresar cantidades económicas dentro de las operaciones comerciales del Marketplace.

Se propone como Value Object para evitar representar los valores monetarios como tipos primitivos dispersos dentro de las entidades del dominio.

## Attributes

| Attribute  | Type      | Description                              |
| ---------- | --------- | ---------------------------------------- |
| `amount`   | `Decimal` | Valor numérico de la cantidad monetaria. |
| `currency` | `String`  | Moneda en la que se expresa el valor.    |

## Usage

Money puede utilizarse en los elementos que necesiten representar valores económicos, como el precio de un producto o el valor asociado a una operación comercial.

```text
Product
   │
   └── price ────────────────> Money

OrderItem
   │
   └── price ────────────────> Money
```

### Business Rules

- El valor monetario debe representar una cantidad válida.
- La moneda debe estar definida para el valor.
- Dos valores monetarios solamente deben considerarse equivalentes cuando representan el mismo valor y moneda.

### Responsibilities

Las principales responsabilidades de Money son:

- Representar valores monetarios.
- Mantener la cantidad y la moneda asociada.
- Evitar la dispersión de información monetaria dentro de las entidades.

### Design Notes

Money es una propuesta de diseño del modelo y no una entidad explícitamente definida por la especificación funcional.

La especificación proporcionada no define actualmente la estructura interna de los precios, monedas, impuestos o descuentos.

Por esta razón, Money se mantiene como un Value Object conceptual y sus reglas detalladas deberán ajustarse si posteriormente se agregan requisitos financieros específicos.

---

# Variant

## Description

Variant representa una característica diferenciadora de un producto.

Las variantes permiten representar diferencias como color, talla, modelo u otras características relevantes del producto.

La especificación define explícitamente las variantes como una lista dentro de la información del catálogo.

## Attributes

| Attribute | Type     | Description                                 |
| --------- | -------- | ------------------------------------------- |
| `name`    | `String` | Nombre de la característica de la variante. |
| `value`   | `String` | Valor específico de la característica.      |

## Usage

Las variantes pertenecen conceptualmente a Product.

```text
Product
   │
   └── variants ─────────────> Variant
```

Un producto puede tener cero o múltiples variantes.

Ejemplos conceptuales:

- Color = Negro
- Talla = M
- Modelo = Pro

### Business Rules

- Las variantes representan diferencias entre versiones de un producto.
- Un producto puede tener múltiples variantes.
- Una variante debe identificar una característica y su valor.
- Las variantes forman parte de la información del catálogo del producto.

### Responsibilities

Las principales responsabilidades de Variant son:

- Representar una característica diferenciadora de un producto.
- Mantener el nombre de la característica.
- Mantener el valor correspondiente.

### Design Notes

Variant se modela como Value Object porque representa una característica descriptiva del producto y no requiere una identidad independiente dentro del dominio.

La especificación menciona explícitamente ejemplos como color, talla y modelo, pero no establece una estructura más detallada. Por ello, el modelo utiliza `name` y `value` como representación general.
