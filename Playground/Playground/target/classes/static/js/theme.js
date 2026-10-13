/**
 * theme.js — Selector de tema claro/oscuro para Playground
 * ----------------------------------------------------------
 * El tema se guarda en localStorage bajo la clave "pg-theme" ("dark" | "light")
 * y se aplica poniendo el atributo data-theme en <html>. Los estilos del
 * tema claro están definidos en styles.css bajo html[data-theme="light"].
 *
 * IMPORTANTE: en cada plantilla hay, justo dentro de <head>, un pequeño
 * script inline que aplica el tema guardado ANTES de pintar la página
 * (evita el parpadeo / FOUC). Este archivo solo se encarga de: pintar el
 * icono correcto del botón y reaccionar al click para cambiar de tema.
 */
(function () {
  var STORAGE_KEY = 'pg-theme';

  function temaActual() {
    return document.documentElement.getAttribute('data-theme') === 'light' ? 'light' : 'dark';
  }

  function actualizarBoton(boton, tema) {
    if (!boton) return;
    boton.textContent = tema === 'light' ? '🌙' : '☀️';
    boton.setAttribute('aria-label', tema === 'light' ? 'Cambiar a tema oscuro' : 'Cambiar a tema claro');
    boton.title = boton.getAttribute('aria-label');
  }

  function aplicarTema(tema) {
    document.documentElement.setAttribute('data-theme', tema);
    localStorage.setItem(STORAGE_KEY, tema);
    document.querySelectorAll('[data-theme-toggle]').forEach(function (btn) {
      actualizarBoton(btn, tema);
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-theme-toggle]').forEach(function (btn) {
      actualizarBoton(btn, temaActual());
      btn.addEventListener('click', function () {
        aplicarTema(temaActual() === 'light' ? 'dark' : 'light');
      });
    });
  });
})();
