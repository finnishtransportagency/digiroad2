(function(root) {
  root.EditModeDisclaimer = {
    initialize: initialize
  };

  function initialize(instructionsPopup) {
    var editMessage = $(
      '<div class="action-state">' +
      '  Olet muokkaustilassa. Kuntakäyttäjien tulee kohdistaa muutokset katuverkolle, ELY-käyttäjien maantieverkolle.' +
      '</div>');

    var editMessageControlTR = $(
        '<div class="controlTR">' +
        '  Pysäkin varustetietoja ylläpidetään Tierekisterissä.' +
        '</div>');

    function handleEditMessage(readOnly) {
      if (readOnly) {
        editMessage.hide();
      } else {
        editMessage.show();
      }
    }

    function handleTRControlMessage(showMessage) {
      if (showMessage) {
        editMessageControlTR.show();
      } else {
        editMessageControlTR.hide();
      }
    }

    function showEditInstructionsPopup(readOnly) {
      if(!readOnly) {
        instructionsPopup.show('Kuntakäyttäjien tulee kohdistaa muutokset katuverkolle, ELY-käyttäjien maantieverkolle.', 4000);
      }
    }

    eventbus.on('application:readOnly', function(readOnly) {
      handleEditMessage(readOnly);
      showEditInstructionsPopup(readOnly);
    });
    
    eventbus.on('application:controledTR', function(show) {
      handleTRControlMessage(show);
    });

    $('#header').append(editMessage.hide());
    $('#header').append(editMessageControlTR.hide());
  }
})(this);
