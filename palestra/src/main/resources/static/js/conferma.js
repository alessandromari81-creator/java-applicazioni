/*
 * FILE: conferma.js
 * A COSA SERVE: fa chiedere conferma, con una finestra di dialogo, prima di inviare un modulo. Ogni <form data-conferma="domanda"> della pagina
 * apre la finestra #finestra-conferma (definita in templates/fragments/conferma.html) con quella domanda: «Conferma» invia il modulo,
 * «Annulla» e il tasto Esc lo lasciano com'è.
 * Se il browser non supporta <dialog> lo script non fa niente e i moduli si inviano subito. Il server controlla comunque ogni richiesta.
 */
(function () {
  var finestra = document.getElementById('finestra-conferma');
  if (!finestra || typeof finestra.showModal !== 'function') {
    return;
  }

  var testo = document.getElementById('conferma-testo');
  var daInviare = null;   // il modulo in attesa di conferma

  // Un solo ascoltatore per tutta la pagina: intercetta l'invio di qualunque modulo con data-conferma.
  document.addEventListener('submit', function (evento) {
    var modulo = evento.target;
    if (!modulo.matches || !modulo.matches('form[data-conferma]')) {
      return;
    }
    evento.preventDefault();                                // ferma l'invio: prima si chiede
    daInviare = modulo;
    testo.textContent = modulo.getAttribute('data-conferma');   // textContent: il testo non viene mai interpretato come HTML
    finestra.showModal();
    document.getElementById('conferma-annulla').focus();    // il focus parte su «Annulla»: premere Invio per sbaglio non conferma
  });

  document.getElementById('conferma-annulla').addEventListener('click', function () {
    daInviare = null;
    finestra.close();
  });

  document.getElementById('conferma-ok').addEventListener('click', function () {
    var modulo = daInviare;
    daInviare = null;
    finestra.close();
    if (modulo) {
      modulo.submit();   // submit() non riattiva l'evento «submit»: non si richiede conferma due volte
    }
  });

  // Tasto Esc: la finestra si chiude da sola e il modulo non parte.
  finestra.addEventListener('cancel', function () {
    daInviare = null;
  });
})();
