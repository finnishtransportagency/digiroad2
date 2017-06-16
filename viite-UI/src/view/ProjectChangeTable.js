(function(root) {
  root.ProjectChangeTable = function() {
    var changeTable =
      $('<div class="change-table-frame"></div>');
    // Text about validation success hard-coded now
    // TODO: handle status-text for real
    // TODO: table not responsive
    changeTable.append('<div class="change-table-header">Validointi ok. Alla näet muutokset projektissa.</div>');
    changeTable.append('<button class="close btn-close">x</button>');
    changeTable.append('<div class="change-table-borders"></div>' +
      '<div id ="change-table-borders-changetype"></div>' +
      '<div id ="change-table-borders-source"></div>' +
      '<div id ="change-table-borders-target"></div>');
    changeTable.append('<div class="change-table-sections">' +
      '<label class="change-table-heading-label" style="width: 122px">Muutos</label>' +
      '<label class="change-table-heading-label" style="width: 387px">Nykyosoite</label>' +
      '<label class="change-table-heading-label" style="width: 387px">Uusi osoite</label>');
    changeTable.append('<div class="project-changes"></div>');
    changeTable.append('<div><button class="new btn btn-primary close" id="change-table-button-close">Sulje</button></div>');

    function show(){
      $('.container').append(changeTable.toggle());
      bindEvents();
    }

    function hide() {
      changeTable.hide();
    }

    // Dummy solution for testing
    var projectChangeData = {
      changeType: "Lakkautettu",
      source_roadNumber: "test",
      source_trackCode: "test",
      source_startRoadPartNumber: "test",
      source_startAddressM: "test",
      source_endRoadPartNumber: "test",
      source_endAddressM: "test",
      target_roadNumber: "test",
      target_trackCode: "test",
      target_startRoadPartNumber: "test",
      target_startAddressM: "test",
      target_endRoadPartNumber: "test",
      target_endAddressM: "test",
      discontinuity: "test",
      roadType: "test",
      ely: 99
    };

    function bindEvents(){

      var htmlTable =
        '<table class="change-table">' +
          '<tr class="change-table-headers">' +
            '<td class="project-change-table-dimension-first"></td>'+
            '<td class="project-change-table-dimension">TIE</td>'+
            '<td class="project-change-table-dimension">AJR</td>'+
            '<td class="project-change-table-dimension">AOSA</td>'+
            '<td class="project-change-table-dimension">AET</td>'+
            '<td class="project-change-table-dimension">LOSA</td>'+
            '<td class="project-change-table-dimension">LET</td>'+
            '<td class="project-change-table-dimension">TIE</td>'+
            '<td class="project-change-table-dimension">AJR</td>'+
            '<td class="project-change-table-dimension">AOSA</td>'+
            '<td class="project-change-table-dimension">AET</td>'+
            '<td class="project-change-table-dimension">LOSA</td>'+
            '<td class="project-change-table-dimension">LET</td>'+
            '<td class="project-change-table-dimension">JATKUU</td>'+
            '<td class="project-change-table-dimension">TIETYYPPI</td>'+
            '<td class="project-change-table-dimension">ELY</td>'+
          '</tr>';
          // TODO: Get every change object from backend and loop rows
          htmlTable += '<tr class="change-table-data-row">' +
            '<td class="project-change-table-dimension-first">' + projectChangeData.changeType + '</td>'+
            '<td class="project-change-table-data-cell">' + projectChangeData.source_roadNumber + '</td>'+
            '<td class="project-change-table-data-cell">' + projectChangeData.source_trackCode + '</td>'+
            '<td class="project-change-table-data-cell">' + projectChangeData.source_startRoadPartNumber + '</td>'+
            '<td class="project-change-table-data-cell">' + projectChangeData.source_startAddressM + '</td>'+
            '<td class="project-change-table-data-cell">' + projectChangeData.source_endRoadPartNumber + '</td>'+
            '<td class="project-change-table-data-cell">' + projectChangeData.source_endAddressM + '</td>'+
            '<td class="project-change-table-data-cell">' + projectChangeData.target_roadNumber + '</td>'+
            '<td class="project-change-table-data-cell">' + projectChangeData.target_trackCode + '</td>'+
            '<td class="project-change-table-data-cell">' + projectChangeData.target_startRoadPartNumber + '</td>'+
            '<td class="project-change-table-data-cell">' + projectChangeData.target_startAddressM + '</td>'+
            '<td class="project-change-table-data-cell">' + projectChangeData.target_endRoadPartNumber + '</td>'+
            '<td class="project-change-table-data-cell">' + projectChangeData.target_endAddressM + '</td>'+
            '<td class="project-change-table-data-cell">' + projectChangeData.discontinuity + '</td>'+
            '<td class="project-change-table-data-cell">' + projectChangeData.roadType + '</td>'+
            '<td class="project-change-table-data-cell">' + projectChangeData.ely + '</td>'+
            '</tr>';

        htmlTable += '</table>';

      $('.project-changes').html($(htmlTable));

      changeTable.on('click', 'button.close', function (){
        hide();
      });
    }

    return{
      show: show,
      hide: hide,
      bindEvents: bindEvents
    };
  };
})(this);