# Сервер: кілька колоній на гравця

MineColonies `1.1.1403-1.21.1` має ключ `allowinfinitecolonies` у `config/minecolonies-server.toml` (`ServerConfiguration.allowInfiniteColonies`, default `false`).

Цей ключ не дозволяє заснувати другу колонію. Єдине місце, де він читається — клієнтське вікно `WindowTownHallDeleteAbandonColony`: при `true` показується кнопка abandon замість delete. Сервер у `CreateColonyMessage.onExecute` все одно відхиляє заснування, якщо `IColonyManager.getIColonyByOwner` вже повертає колонію.

Тому в аддоні стоїть `CreateColonyMessageMixin`: якщо гравець уже має державу Colony Nations, перевірка власника пропускається і друга (і подальші) колонії створюються. Перевірки «ратуша на землі своєї держави» ще немає — її додасть етап територій.

Гравець без держави як і раніше може мати лише одну колонію MineColonies.
