/**
 * i18n.js — Selector de idioma (Español / English) para Playground
 * -------------------------------------------------------------------
 * Traducción 100% en el navegador (igual que currency.js con las monedas):
 * no requiere cambios en el backend. El idioma se guarda en localStorage
 * bajo la clave "pg-lang" ("es" | "en") y se aplica reemplazando el texto
 * de todo elemento marcado con el atributo data-i18n="clave" usando el
 * diccionario DICCIONARIO de abajo. Los placeholders de inputs se
 * traducen con data-i18n-placeholder="clave".
 *
 * El contenido dinámico que viene de la base de datos (nombres de juegos,
 * descripciones, etc.) NO se traduce, solo los textos fijos de la interfaz.
 *
 * Para añadir una traducción nueva en una plantilla:
 *   <h2 data-i18n="dashboard.titulo">Todos los Juegos</h2>
 * y añadir la clave "dashboard.titulo" en los dos idiomas del diccionario.
 */
(function () {
  var STORAGE_KEY = 'pg-lang';

  var DICCIONARIO = {
    es: {
      'nav.inicio': 'Inicio',
      'nav.buscar': 'Buscar',
      'nav.accion': 'Acción',
      'nav.rpg': 'RPG',
      'nav.shooter': 'Shooter',
      'nav.wishlist': '❤ Wishlist',
      'nav.perfil': 'Mi perfil',
      'nav.admin': 'Admin',
      'nav.login': 'Iniciar sesión',
      'nav.logout': 'Salir',

      'footer.derechos': '© 2026 — PLAYGROUND — Todos los derechos reservados',

      'hero.eyebrow': 'Compara precios de videojuegos',
      'hero.titulo1': 'ENCUENTRA EL',
      'hero.titulo2': 'MEJOR PRECIO',
      'hero.subtitulo': 'Steam · Epic Games · GOG — todo en un solo lugar.',
      'hero.catalogo': 'Ver Catálogo',
      'hero.buscarJuego': '🔍 Buscar juego',

      'dashboard.todos': 'Todos los Juegos',
      'dashboard.verTodos': 'Ver todos →',
      'dashboard.accionTitulo': 'Juegos de Acción',
      'dashboard.rpgTitulo': 'Juegos de Rol',
      'dashboard.shooterTitulo': 'Juegos de Disparos',
      'dashboard.aventuraTitulo': 'Juegos de Aventura',
      'dashboard.estrategiaTitulo': 'Juegos de Estrategia',
      'dashboard.anterior': '← Anterior',
      'dashboard.siguiente': 'Siguiente →',
      'dashboard.comparar': 'Comparar',
      'dashboard.ver': 'Ver',
      'tab.todos': 'Todos',

      'login.titulo': 'INICIAR SESIÓN',
      'login.usuario': 'Usuario o correo',
      'login.password': 'Contraseña',
      'login.entrar': 'ENTRAR',
      'login.google': 'Continuar con Google',
      'login.sinCuenta': '¿No tienes cuenta?',
      'login.registrate': 'Regístrate',
      'login.errorCredenciales': 'Usuario o contraseña incorrectos',

      'registro.titulo': 'CREAR CUENTA',
      'registro.usuario': 'Nombre de usuario *',
      'registro.email': 'Email *',
      'registro.telefono': 'Teléfono',
      'registro.opcional': '(opcional)',
      'registro.direccion': 'Dirección',
      'registro.password': 'Contraseña *',
      'registro.confirmar': 'Confirmar contraseña *',
      'registro.boton': 'REGISTRARSE',
      'registro.yaTienes': '¿Ya tienes cuenta?',
      'registro.iniciarSesion': 'Iniciar sesión',

      'buscador.titulo': 'Buscar juegos',
      'buscador.placeholder': 'Nombre del juego...',
      'buscador.filtrar': 'Filtrar',
      'buscador.genero': 'Género',
      'buscador.todos': 'Todos',
      'buscador.sinResultados': 'No se encontraron juegos',

      'wishlist.titulo': 'Mi Wishlist',
      'wishlist.vacia': 'Aún no has añadido juegos a tu wishlist',

      'perfil.titulo': 'Mi perfil',
      'perfil.datos': 'Datos personales',
      'perfil.guardar': 'Guardar cambios',
      'perfil.cambiarPassword': 'Cambiar contraseña',

      'admin.titulo': 'Panel de administración',
      'admin.nuevoJuego': 'Nuevo juego',
      'admin.usuarios': 'Usuarios',
      'admin.juegos': 'Juegos',

      'error.titulo': 'Ups, algo salió mal',
      'error.volver': 'Volver al inicio',

      'theme.label': 'Tema',
      'lang.label': 'Idioma'
    },
    en: {
      'nav.inicio': 'Home',
      'nav.buscar': 'Search',
      'nav.accion': 'Action',
      'nav.rpg': 'RPG',
      'nav.shooter': 'Shooter',
      'nav.wishlist': '❤ Wishlist',
      'nav.perfil': 'My profile',
      'nav.admin': 'Admin',
      'nav.login': 'Sign in',
      'nav.logout': 'Log out',

      'footer.derechos': '© 2026 — PLAYGROUND — All rights reserved',

      'hero.eyebrow': 'Compare video game prices',
      'hero.titulo1': 'FIND THE',
      'hero.titulo2': 'BEST PRICE',
      'hero.subtitulo': 'Steam · Epic Games · GOG — all in one place.',
      'hero.catalogo': 'View Catalog',
      'hero.buscarJuego': '🔍 Search a game',

      'dashboard.todos': 'All Games',
      'dashboard.verTodos': 'See all →',
      'dashboard.accionTitulo': 'Action Games',
      'dashboard.rpgTitulo': 'Role-Playing Games',
      'dashboard.shooterTitulo': 'Shooter Games',
      'dashboard.aventuraTitulo': 'Adventure Games',
      'dashboard.estrategiaTitulo': 'Strategy Games',
      'dashboard.anterior': '← Previous',
      'dashboard.siguiente': 'Next →',
      'dashboard.comparar': 'Compare',
      'dashboard.ver': 'View',
      'tab.todos': 'All',

      'login.titulo': 'SIGN IN',
      'login.usuario': 'Username or email',
      'login.password': 'Password',
      'login.entrar': 'SIGN IN',
      'login.google': 'Continue with Google',
      'login.sinCuenta': "Don't have an account?",
      'login.registrate': 'Sign up',
      'login.errorCredenciales': 'Incorrect username or password',

      'registro.titulo': 'CREATE ACCOUNT',
      'registro.usuario': 'Username *',
      'registro.email': 'Email *',
      'registro.telefono': 'Phone',
      'registro.opcional': '(optional)',
      'registro.direccion': 'Address',
      'registro.password': 'Password *',
      'registro.confirmar': 'Confirm password *',
      'registro.boton': 'SIGN UP',
      'registro.yaTienes': 'Already have an account?',
      'registro.iniciarSesion': 'Sign in',

      'buscador.titulo': 'Search games',
      'buscador.placeholder': 'Game name...',
      'buscador.filtrar': 'Filter',
      'buscador.genero': 'Genre',
      'buscador.todos': 'All',
      'buscador.sinResultados': 'No games found',

      'wishlist.titulo': 'My Wishlist',
      'wishlist.vacia': "You haven't added any games to your wishlist yet",

      'perfil.titulo': 'My profile',
      'perfil.datos': 'Personal information',
      'perfil.guardar': 'Save changes',
      'perfil.cambiarPassword': 'Change password',

      'admin.titulo': 'Admin panel',
      'admin.nuevoJuego': 'New game',
      'admin.usuarios': 'Users',
      'admin.juegos': 'Games',

      'error.titulo': 'Oops, something went wrong',
      'error.volver': 'Back to home',

      'theme.label': 'Theme',
      'lang.label': 'Language'
    }
  };

  function idiomaActual() {
    var guardado = localStorage.getItem(STORAGE_KEY);
    return guardado === 'en' ? 'en' : 'es';
  }

  // --------------------------------------------------------------------
  // Traducción automática (para todo lo que NO tiene data-i18n): usa la
  // API pública y gratuita de MyMemory (no requiere API key ni backend).
  // Los resultados se cachean en localStorage para no volver a pedirlos
  // y para que las páginas carguen instantáneo en visitas siguientes.
  // Limitación: depende de tener internet y de la cuota gratuita del
  // servicio (uso normal de una app pequeña no debería alcanzarla), y la
  // calidad es la de un traductor automático (no siempre perfecta).
  // --------------------------------------------------------------------
  var TRANSLATE_API = 'https://api.mymemory.translated.net/get';
  var CACHE_KEY = 'pg-auto-translate-cache-en';
  var LOTE_CONCURRENTE = 4; // cuántas peticiones a la API en paralelo
  var cache = cargarCache();

  function cargarCache() {
    try { return JSON.parse(localStorage.getItem(CACHE_KEY) || '{}'); }
    catch (e) { return {}; }
  }

  function guardarCache() {
    try { localStorage.setItem(CACHE_KEY, JSON.stringify(cache)); } catch (e) { /* almacenamiento lleno o no disponible */ }
  }

  function soloNumerosOSimbolos(texto) {
    return /^[\d\s.,$€%:\/\-()+#*]*$/.test(texto);
  }

  // Un elemento es "traducible automáticamente" si es una hoja de texto
  // (no tiene elementos hijos, solo texto), no está ya cubierto por el
  // diccionario (data-i18n), y no es un precio ni parte de los controles.
  function esAutoTraducible(el) {
    if (el.tagName === 'SCRIPT' || el.tagName === 'STYLE' || el.tagName === 'NOSCRIPT') return false;
    if (el.closest('.pg-controls')) return false;
    if (el.hasAttribute('data-i18n') || el.hasAttribute('data-no-translate')) return false;
    if (el.hasAttribute('data-precio-base')) return false; // lo maneja currency.js
    for (var i = 0; i < el.childNodes.length; i++) {
      if (el.childNodes[i].nodeType === 1) return false; // tiene hijos elemento: no es hoja
    }
    var texto = el.textContent.trim();
    if (!texto || soloNumerosOSimbolos(texto)) return false;
    return true;
  }

  function recolectarElementosAutoTraducibles() {
    return Array.prototype.filter.call(document.querySelectorAll('body *'), esAutoTraducible);
  }

  function recolectarPlaceholdersAutoTraducibles() {
    return Array.prototype.filter.call(
      document.querySelectorAll('input[placeholder], textarea[placeholder]'),
      function (el) { return !el.hasAttribute('data-i18n-placeholder') && el.getAttribute('placeholder').trim(); }
    );
  }

  function traducirTexto(texto) {
    if (cache[texto]) return Promise.resolve(cache[texto]);
    var url = TRANSLATE_API + '?q=' + encodeURIComponent(texto) + '&langpair=es|en';
    return fetch(url)
      .then(function (r) { return r.json(); })
      .then(function (data) {
        var traducido = data && data.responseData && data.responseData.translatedText;
        if (traducido) {
          cache[texto] = traducido;
          guardarCache();
          return traducido;
        }
        return texto;
      })
      .catch(function () { return texto; }); // sin red o falla la API: deja el texto original
  }

  function procesarEnLotes(elementos, aplicar) {
    var idx = 0;
    function siguienteLote() {
      var lote = elementos.slice(idx, idx + LOTE_CONCURRENTE);
      idx += LOTE_CONCURRENTE;
      if (!lote.length) return;
      Promise.all(lote.map(function (el) {
        return aplicar(el);
      })).then(siguienteLote);
    }
    siguienteLote();
  }

  function autoTraducirATexto(el) {
    if (!el.hasAttribute('data-i18n-orig')) {
      el.setAttribute('data-i18n-orig', el.textContent);
    }
    var original = el.getAttribute('data-i18n-orig');
    return traducirTexto(original).then(function (traducido) {
      if (idiomaActual() === 'en') el.textContent = traducido; // evita pisar si el usuario ya volvió a cambiar
    });
  }

  function autoTraducirPlaceholder(el) {
    if (!el.hasAttribute('data-placeholder-orig')) {
      el.setAttribute('data-placeholder-orig', el.getAttribute('placeholder'));
    }
    var original = el.getAttribute('data-placeholder-orig');
    return traducirTexto(original).then(function (traducido) {
      if (idiomaActual() === 'en') el.setAttribute('placeholder', traducido);
    });
  }

  function autoTraducirPagina() {
    procesarEnLotes(recolectarElementosAutoTraducibles(), autoTraducirATexto);
    procesarEnLotes(recolectarPlaceholdersAutoTraducibles(), autoTraducirPlaceholder);
  }

  function restaurarAutoTraducciones() {
    document.querySelectorAll('[data-i18n-orig]').forEach(function (el) {
      el.textContent = el.getAttribute('data-i18n-orig');
    });
    document.querySelectorAll('[data-placeholder-orig]').forEach(function (el) {
      el.setAttribute('placeholder', el.getAttribute('data-placeholder-orig'));
    });
  }
  // --------------------------------------------------------------------

  function traducirPagina(idioma) {
    var dic = DICCIONARIO[idioma] || DICCIONARIO.es;

    document.querySelectorAll('[data-i18n]').forEach(function (el) {
      var clave = el.getAttribute('data-i18n');
      if (dic[clave]) {
        el.textContent = dic[clave];
      }
    });

    document.querySelectorAll('[data-i18n-placeholder]').forEach(function (el) {
      var clave = el.getAttribute('data-i18n-placeholder');
      if (dic[clave]) {
        el.setAttribute('placeholder', dic[clave]);
      }
    });

    document.documentElement.setAttribute('lang', idioma);

    document.querySelectorAll('[data-lang-select]').forEach(function (select) {
      select.value = idioma;
    });

    // El resto del texto de la página (descripciones, nombres de juegos,
    // botones sin data-i18n, etc.) se traduce solo si el idioma es inglés;
    // al volver a español se restaura el texto original guardado.
    if (idioma === 'en') {
      autoTraducirPagina();
    } else {
      restaurarAutoTraducciones();
    }
  }

  function aplicarIdioma(idioma) {
    localStorage.setItem(STORAGE_KEY, idioma);
    traducirPagina(idioma);
  }

  document.addEventListener('DOMContentLoaded', function () {
    traducirPagina(idiomaActual());

    document.querySelectorAll('[data-lang-select]').forEach(function (select) {
      select.addEventListener('change', function () {
        aplicarIdioma(select.value);
      });
    });
  });
})();
