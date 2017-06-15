(function(root) {
  root.ProjectChangeTable = function() {
    var changeTable =
      $('<div class="change-table-frame"></div>');
    changeTable.append('<div class="change-table-header">Validointi ok. Alla näet muutokset projektissa.</div>');
    changeTable.append('<button class="close btn-close">x</button>');
    changeTable.append('<div class="change-table-sections">' +
      '<label class="change-table-heading-label">Muutos</label>' +
      '<label class="change-table-heading-label">Nykyosoite</label>' +
      '<label class="change-table-heading-label">Uusi osoite</label>');
    changeTable.append('<div class="project-changes"></div>');
    changeTable.append('<div><button class="new btn btn-primary close" id="change-table-button-close">Sulje</button></div>');

    function show(){
      $('.container').append(changeTable.toggle());
      bindEvents();
    }

    function hide() {
      changeTable.hide();
    }

    function bindEvents(){
      changeTable.on('click', 'button.close', function (){
        hide();
      });
    }

    /*
    var projectChanges =
      '<table>' +
      '<td></td>'+
      '<td></td>'+
      '<td></td>'+
      '</table>';
    */

    return{
      show: show,
      hide: hide,
      bindEvents: bindEvents
    };
  };
})(this);