/**
 * currency.js — Selector de moneda para Playground
 * ---------------------------------------------------
 * Convierte en el navegador todos los precios marcados con el atributo
 * data-precio-base="valor" a la moneda que el usuario elija.
 *
 * IMPORTANTE: la moneda en la que ya venían guardados los precios del
 * proyecto es el PESO COLOMBIANO (COP), no dólares. Por eso BASE_CURRENCY
 * = "COP" más abajo. Si en algún momento el proyecto cambia la moneda en
 * la que se guardan los precios en Mongo, ese es el único valor que hay
 * que tocar aquí.
 *
 * No requiere cambios en el backend: la conversión es solo visual, en el
 * navegador. Por defecto (antes de que el usuario elija algo) se muestra
 * en COP, es decir, exactamente como estaba el proyecto antes de esta
 * tarea — nada cambia hasta que el usuario use el selector.
 *
 * Uso en una plantilla Thymeleaf:
 *   <span class="precio" th:attr="data-precio-base=${juego.precioMinimo}"
 *         th:text="'$' + ${#numbers.formatDecimal(juego.precioMinimo,1,0)}"></span>
 *   ...
 *   <script th:src="@{/js/currency.js}"></script>
 *
 * El script busca automáticamente el patrón "$<numero>" dentro del texto de
 * cada elemento [data-precio-base] y lo reemplaza, conservando el resto del
 * texto (por ejemplo "Desde $59.990" -> "Desde US$ 15,20").
 */
(function () {
  "use strict";

  var BASE_CURRENCY = "COP"; // moneda en la que ya estaban guardados los precios

  var STORAGE_KEY = "pg_currency";
  var RATES_KEY = "pg_currency_rates";
  var RATES_TS_KEY = "pg_currency_rates_ts";
  var RATES_TTL_MS = 6 * 60 * 60 * 1000; // 6 horas
  var RATES_ENDPOINT = "https://open.er-api.com/v6/latest/USD";

  // Tasas de respaldo (aprox., unidades por 1 USD) por si falla la petición
  // a la API o no hay red. Todas las tasas (de la API o de aquí) están
  // expresadas en la misma base: "cuántas unidades de X equivalen a 1 USD".
  var FALLBACK_RATES = {
    USD: 1,
    COP: 4100,
    MXN: 18.5,
    ARS: 1000,
    CLP: 950,
    PEN: 3.75,
    BRL: 5.4,
    UYU: 40.5,
    BOB: 6.9,
    PYG: 7300,
    VES: 55,
    CRC: 515,
    GTQ: 7.7,
    DOP: 60,
    HNL: 25,
    NIO: 36.7,
    EUR: 0.92,
    GBP: 0.79,
    CAD: 1.38,
    AUD: 1.52,
    CHF: 0.88,
    JPY: 150,
    CNY: 7.1,
    KRW: 1380
  };

  var CURRENCIES = [
    { code: "COP", name: "Peso colombiano" },
    { code: "USD", name: "Dólar estadounidense" },
    { code: "MXN", name: "Peso mexicano" },
    { code: "ARS", name: "Peso argentino" },
    { code: "CLP", name: "Peso chileno" },
    { code: "PEN", name: "Sol peruano" },
    { code: "BRL", name: "Real brasileño" },
    { code: "UYU", name: "Peso uruguayo" },
    { code: "BOB", name: "Boliviano" },
    { code: "PYG", name: "Guaraní paraguayo" },
    { code: "VES", name: "Bolívar venezolano" },
    { code: "CRC", name: "Colón costarricense" },
    { code: "GTQ", name: "Quetzal guatemalteco" },
    { code: "DOP", name: "Peso dominicano" },
    { code: "HNL", name: "Lempira hondureño" },
    { code: "NIO", name: "Córdoba nicaragüense" },
    { code: "EUR", name: "Euro" },
    { code: "GBP", name: "Libra esterlina" },
    { code: "CAD", name: "Dólar canadiense" },
    { code: "AUD", name: "Dólar australiano" },
    { code: "CHF", name: "Franco suizo" },
    { code: "JPY", name: "Yen japonés" },
    { code: "CNY", name: "Yuan chino" },
    { code: "KRW", name: "Won surcoreano" }
  ];

  var state = {
    currency: localStorage.getItem(STORAGE_KEY) || BASE_CURRENCY,
    rates: loadCachedRates() || FALLBACK_RATES
  };

  function loadCachedRates() {
    try {
      var ts = parseInt(localStorage.getItem(RATES_TS_KEY) || "0", 10);
      if (!ts || Date.now() - ts > RATES_TTL_MS) return null;
      var raw = localStorage.getItem(RATES_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch (e) {
      return null;
    }
  }

  function fetchRates() {
    fetch(RATES_ENDPOINT)
      .then(function (res) { return res.json(); })
      .then(function (data) {
        if (data && data.result === "success" && data.rates) {
          state.rates = data.rates;
          localStorage.setItem(RATES_KEY, JSON.stringify(data.rates));
          localStorage.setItem(RATES_TS_KEY, String(Date.now()));
          applyConversion();
        }
      })
      .catch(function () {
        // Sin conexión o API caída: seguimos usando FALLBACK_RATES / caché previa.
      });
  }

  /**
   * Convierte un monto expresado en BASE_CURRENCY a la moneda destino.
   * Las tasas (de la API o de respaldo) están expresadas como "unidades
   * de X por 1 USD", así que primero pasamos el monto a USD y de ahí a
   * la moneda destino.
   */
  function convertir(monto, monedaDestino) {
    var tasaBase = state.rates[BASE_CURRENCY] || FALLBACK_RATES[BASE_CURRENCY] || 1;
    var tasaDestino = state.rates[monedaDestino] || FALLBACK_RATES[monedaDestino] || 1;
    var montoUsd = monto / tasaBase;
    return montoUsd * tasaDestino;
  }

  function formatCurrency(monto, code) {
    if (code === BASE_CURRENCY) {
      // Sin conversión: se muestra tal cual estaba el proyecto originalmente.
      try {
        return new Intl.NumberFormat(undefined, {
          style: "currency",
          currency: code,
          maximumFractionDigits: 0
        }).format(monto);
      } catch (e) {
        return "$" + monto.toFixed(0);
      }
    }

    var converted = convertir(monto, code);
    try {
      return new Intl.NumberFormat(undefined, {
        style: "currency",
        currency: code,
        maximumFractionDigits: converted >= 1000 ? 0 : 2
      }).format(converted);
    } catch (e) {
      return converted.toFixed(2) + " " + code;
    }
  }

  function applyConversion() {
    var nodes = document.querySelectorAll("[data-precio-base]");
    nodes.forEach(function (el) {
      var monto = parseFloat(el.getAttribute("data-precio-base"));
      if (isNaN(monto) || monto <= 0) return;
      var formatted = formatCurrency(monto, state.currency);
      // Reemplaza únicamente el patrón "$123" o "$123.45" dentro del texto,
      // conservando cualquier prefijo como "Desde ".
      var pattern = /\$\s?[\d.,]+/;
      if (pattern.test(el.textContent)) {
        el.textContent = el.textContent.replace(pattern, formatted);
      } else {
        el.textContent = formatted;
      }
    });
  }

  function buildSwitcher() {
    var navbar = document.querySelector(".navbar");
    if (!navbar) return;

    var wrap = document.createElement("div");
    wrap.className = "currency-switcher";

    var button = document.createElement("button");
    button.type = "button";
    button.className = "currency-btn";
    button.textContent = "🌐 " + state.currency + " ▾";

    var menu = document.createElement("div");
    menu.className = "currency-menu";
    menu.hidden = true;

    CURRENCIES.forEach(function (c) {
      var item = document.createElement("button");
      item.type = "button";
      item.className = "currency-item";
      item.textContent = c.code + " — " + c.name;
      item.addEventListener("click", function () {
        state.currency = c.code;
        localStorage.setItem(STORAGE_KEY, c.code);
        button.textContent = "🌐 " + c.code + " ▾";
        menu.hidden = true;
        applyConversion();
      });
      menu.appendChild(item);
    });

    button.addEventListener("click", function (e) {
      e.stopPropagation();
      menu.hidden = !menu.hidden;
    });
    document.addEventListener("click", function () {
      menu.hidden = true;
    });

    wrap.appendChild(button);
    wrap.appendChild(menu);
    navbar.appendChild(wrap);
  }

  function injectStyles() {
    var style = document.createElement("style");
    style.textContent =
      ".currency-switcher{position:relative;margin-left:0.75rem}" +
      ".currency-btn{background:transparent;border:1px solid var(--border,#333);color:var(--muted,#888);" +
      "padding:0.4rem 0.8rem;border-radius:6px;font-size:0.85rem;cursor:pointer}" +
      ".currency-btn:hover{border-color:var(--primary,#00bfff);color:var(--primary,#00bfff)}" +
      ".currency-menu{position:absolute;right:0;top:110%;background:var(--card,#111);" +
      "border:1px solid var(--border,#222);border-radius:8px;min-width:220px;max-height:320px;" +
      "overflow-y:auto;z-index:1000;box-shadow:0 8px 24px rgba(0,0,0,0.3)}" +
      ".currency-item{display:block;width:100%;text-align:left;background:transparent;" +
      "border:none;color:var(--text,#ddd);padding:0.6rem 0.9rem;font-size:0.85rem;cursor:pointer}" +
      ".currency-item:hover{background:rgba(0,191,255,0.12);color:var(--primary,#00bfff)}";
    document.head.appendChild(style);
  }

  document.addEventListener("DOMContentLoaded", function () {
    injectStyles();
    buildSwitcher();
    applyConversion();  // aplica de inmediato con tasas de respaldo/caché
    fetchRates();        // luego actualiza con tasas reales si hay red
  });
})();
