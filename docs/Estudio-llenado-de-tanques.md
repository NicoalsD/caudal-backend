# Estudio: cómo se llena y se vacía un tanque veredal

Este estudio explica cómo funciona el tanque de almacenamiento de un acueducto veredal por gravedad, como los de Guaitarilla. De aquí salen tres cosas de CAUDAL:

- **La escena 3D** del fontanero, que dibuja la entrada, el tanque, el rebose y las salidas por sector.
- **El modelo físico** que usan el simulador y la maqueta virtual.
- **El diseño de la maqueta física**: un tanque, sus sensores y dos tuberías de salida.

> Las cifras son de referencia para la maqueta y el simulador. Para diseñar un tanque real hay que aplicar el RAS (Res. 0330 de 2017, modificada por la Res. 799 de 2021) y los planos del acueducto.

## 1. La cadena del agua, de la fuente a la casa

En una vereda, casi siempre el agua corre sin bombas, solo por la pendiente:

| # | Componente | Qué hace |
|---|---|---|
| 1 | **Captación (bocatoma)** | Toma agua de una quebrada o un nacimiento. Una rejilla deja pasar el agua y atrapa hojas y piedras. |
| 2 | **Aducción** | Tubería o canal que lleva el agua cruda de la bocatoma al desarenador. |
| 3 | **Desarenador** | Tanque pequeño donde el agua va despacio para que la arena se asiente en el fondo. |
| 4 | **Conducción** | Tubería que baja el agua hasta el tanque de almacenamiento. Lleva un caudal casi constante, el que da la fuente. |
| 5 | **Potabilización** (si existe) | Filtro lento o cloración. En muchas veredas solo hay cloración en el tanque. |
| 6 | **Tanque de almacenamiento** | Guarda el agua que llega de forma continua para entregarla en las horas de más consumo. Es el corazón de CAUDAL. |
| 7 | **Red de distribución** | Tuberías que salen del tanque hacia los sectores. Cada sector tiene una **válvula de registro (llave)** que abre o cierra su paso. |

La idea clave es que **la fuente entrega poco pero todo el día, y la gente gasta mucho en pocas horas**. El tanque compensa esa diferencia. Cuando la fuente baja en verano, el tanque no alcanza a llenarse, y la Junta raciona: abre los sectores **por turnos**.

## 2. Partes del tanque

| Parte | Dónde va | Para qué sirve | En la escena 3D | En la maqueta |
|---|---|---|---|---|
| **Entrada** | Arriba, por encima del nivel máximo | Recibe el agua de la conducción. Al entrar desde arriba, el agua del tanque no regresa a la conducción. | Tubo con partículas que caen en el tanque | Manguera que llega de la bomba |
| **Válvula de flotador** (opcional) | En la entrada | Cierra la entrada cuando el tanque se llena. Funciona sola, sin energía. | Boya que sube con el nivel | Interruptor de flotador que corta la bomba |
| **Rebose** | Cerca del borde | Saca el agua que sobra cuando la entrada sigue y el tanque está lleno. Así el agua nunca sale por la tapa. | Tubo lateral que solo fluye al tope | Agujero con manguera de regreso al reservorio |
| **Salida con canastilla** | En el fondo, unos centímetros por encima del piso | Alimenta la red. La canastilla es un filtro que no deja pasar lodo ni basura a las tuberías. | Tubos hacia cada sector | 2 tuberías con electroválvula |
| **Desagüe o lavado** | En el punto más bajo | Vacía el tanque para limpiarlo y sacar el sedimento. | Llave roja en el fondo | Tapón |
| **Ventilación** | Techo | Deja entrar y salir el aire cuando el nivel cambia, sin que entren insectos. | Tubo en "U" invertida | Agujero con malla |
| **Tapa de inspección** | Techo | Deja entrar al tanque para revisar y lavar. | Tapa | Tapa del recipiente |
| **Regla o sensor de nivel** | Dentro | Mide cuánta agua hay. Hoy el fontanero lee una regla pintada; con sensor, la lectura llega sola. | Anillos de nivel y cifra en vivo | Sensor ultrasónico en la tapa |

## 3. Cómo se opera en un día normal

1. **De noche** casi nadie gasta y la entrada sigue llegando, así que **el tanque se llena**. Si llega al tope, el rebose bota lo que sobra, o el flotador cierra la entrada.
2. **En la mañana y al mediodía** sube el consumo y el tanque **se vacía más rápido de lo que se llena**.
3. **Con racionamiento**, el fontanero abre la llave de cada sector solo en su turno. Abrir menos sectores a la vez hace que la presión alcance para las casas más altas y que el tanque no se vacíe del todo.
4. **Una reserva mínima** queda siempre en el tanque para emergencias (incendios, daños). En CAUDAL es el **piso de reserva** que configura la Junta.

**Lavado del tanque**, dos veces al año según los manuales de acueductos rurales:

1. Cerrar la entrada.
2. Vaciar el tanque por el desagüe.
3. Cerrar la salida.
4. Restregar las paredes y desinfectar con una solución de cloro, y dejarla actuar el tiempo indicado (los manuales hablan de 24 h).
5. Enjuagar, cerrar el desagüe y volver a llenar.
6. Abrir la distribución cuando el tanque esté lleno.

## 4. Modelo físico (lo que calculan el simulador y la escena 3D)

### 4.1 Balance de masa

El volumen del tanque cambia según lo que entra y lo que sale:

```
dV/dt = Q_entrada − Σ Q_sector − Q_rebose − Q_fugas
```

- `V = A_tanque · h` para un tanque de paredes rectas, con `A_tanque` el área de la base y `h` la altura del agua.
- `Q_entrada` es casi constante en el día. Cambia por estación (verano o lluvias) y con los daños en la conducción.
- `Q_rebose` es 0 mientras `h < h_rebose`. Cuando llega a `h_rebose`, toma todo el exceso.
- `Q_fugas` es un caudal pequeño y continuo, o uno que aparece de golpe cuando hay un daño.

### 4.2 Lo que sale por una llave depende del nivel

Una salida en el fondo se comporta como un orificio (ley de Torricelli):

```
Q_sector = Cd · A_orificio · √(2 · g · h) · apertura
```

- `Cd` ≈ 0,6 es el coeficiente de descarga.
- `A_orificio` es el área interna del tubo.
- `apertura` va de 0 (cerrada) a 1 (abierta del todo).

**Consecuencia:** con el tanque bajo sale menos agua por la misma llave. La escena 3D tiene que mostrarlo: las partículas de cada tubo van más despacio cuando el nivel baja. Por eso conviene tener los turnos largos cuando hay más agua.

**Ejemplo con la maqueta** (tubo de 1/2", `A ≈ 1,27·10⁻⁴ m²`, `h = 0,30 m`, llave abierta):
`Q = 0,6 · 1,27·10⁻⁴ · √(2 · 9,81 · 0,30) ≈ 1,85·10⁻⁴ m³/s ≈ 11 L/min`.

Con las 2 llaves abiertas, un tanque de 20 L se vacía en menos de un minuto. Por eso la maqueta usa una reducción a 1/4" o una llave de paso parcialmente cerrada en cada salida, y así el ciclo dura varios minutos y se puede ver.

### 4.3 Parámetros configurables

Todos viven en la base de datos (tabla del tanque y del sector), nunca en el código:

| Parámetro | Ejemplo vereda | Ejemplo maqueta |
|---|---|---|
| Altura útil del tanque | 2,5 m | 0,35 m |
| Área de la base | 20 m² | 0,07 m² |
| Altura del rebose | 2,4 m | 0,32 m |
| Caudal de entrada | 1,5 L/s | 3–6 L/min (bomba) |
| Diámetro de salida por sector | 2" | 1/4" a 1/2" |
| Piso de reserva | 25 % | 20 % |

## 5. Qué cambia en CAUDAL por este estudio

- **Escena 3D**: dibuja la entrada alta, el rebose, la salida con canastilla y el desagüe. La velocidad de las partículas sale de `Q_sector`, que depende del nivel.
- **Simulador y maqueta virtual**: usan el balance de 4.1 y la ley de 4.2.
- **Piloto automático**: siempre respeta el piso de reserva, y tiene en cuenta que con el nivel bajo un turno entrega menos agua en el mismo tiempo.
- **Maqueta física**: lleva todas las partes de la tabla 2. El reservorio de abajo y la bomba reemplazan la fuente, para que el agua circule en un lazo cerrado. Detalle en [Simulador y hardware](Simulador-y-hardware.md).

## Fuentes

- Ministerio de Vivienda, Ciudad y Territorio. Resolución 0330 de 2017 (RAS), modificada por la Resolución 799 de 2021. Mencionada en [EMCALI, norma NCO-SE-DA-037](https://www.emcali.com.co/documents/d/acueducto/nco-se-da-037-v2-0).
- Universidad Tecnológica de Panamá, CIHH. [Manual operativo y de administración de acueductos rurales](https://cihh.utp.ac.pa/documentos/2014/pdf/M2.Manual_Operativo_y_de_Administracion_de_Acueductos_Rurales.pdf).
- Superintendencia de Servicios Públicos Domiciliarios. [Informe de prestadores](https://www.superservicios.gov.co/sites/default/files/inline-files/publicacion_3_informe1.pdf) (lavado y llenado del tanque antes de restablecer el servicio).
- UNAD. [Automatización del control del llenado de tanques de almacenamiento para acueductos rurales](https://repository.unad.edu.co/handle/10596/75033) (caso ACUAMIRAMAR, Ibagué).
- IRC WASH. [Manual de operación y mantenimiento de sistemas por gravedad](https://nl.ircwash.org/sites/default/files/221-93MA-18138.pdf).
- Secretaría de Salud de México. [Control de la calidad del agua potable](https://www.gob.mx/cms/uploads/attachment/file/810269/control_agua_potable.pdf) (limpieza del flotador).
- Genebre. [Manual de válvula de flotador](https://test-br.genebre.es/download/file/ZG9jdW1lbnRvcy9tYW51YWxlcy9FU18yODU2LnBkZg==) (funciona sin energía y no protege contra sobrepresión).
