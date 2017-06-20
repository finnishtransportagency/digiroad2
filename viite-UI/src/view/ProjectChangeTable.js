(function(root) {
  root.ProjectChangeTable = function(projectChangeInfoModel, projectCollection) {

    var changeTypes = [
      'Käsittelemättä',
      'Ennallaan',
      'Uusi',
      'Siirto',
      'Numerointi',
      'Lakkautettu'
    ];

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
      '<label class="change-table-heading-label" style="width: 117px">Ilmoitus</label>' +
      '<label class="change-table-heading-label" style="width: 379px">Nykyosoite</label>' +
      '<label class="change-table-heading-label" style="width: 379px">Uusi osoite</label>');
    changeTable.append('<div class="change-table-dimension-headers">' +
      '<table class="change-table-dimensions">' +
      '<tr>' +
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
      '</tr>' +
      '</table>' +
      '</div>');
    changeTable.append('<div class="project-changes"></div>');
    changeTable.append('<div><button class="new btn btn-primary close" id="change-table-button-close">Sulje</button></div>');

    function show(){
      $('.container').append(changeTable.toggle());
      bindEvents();
      getChanges();
    }

    function hide() {
      changeTable.hide();
    }

    function getChangeType(type){
      return changeTypes[type];
    }

    function getChanges() {
      var currentProject = projectCollection.getCurrentProject();
      projectChangeInfoModel.getChanges(currentProject.project.id);
    }

    function bindEvents(){
      eventbus.once('projectChanges:fetched', function(projectChangeData){
        var linkForm = new LinkPropertyForm(1);
        var htmlTable ='<table class="change-table">';
        _.each(projectChangeData.changeInfoSeq, function(changeInfoSeq) {
          htmlTable += '<tr class="change-table-data-row">' +
            '<td class="project-change-table-dimension-first">' + getChangeType(changeInfoSeq.changetype) + '</td>' +
            '<td class="project-change-table-data-cell">' + changeInfoSeq.source.roadNumber + '</td>' +
            '<td class="project-change-table-data-cell">' + changeInfoSeq.source.trackCode + '</td>' +
            '<td class="project-change-table-data-cell">' + changeInfoSeq.source.startRoadPartNumber + '</td>' +
            '<td class="project-change-table-data-cell">' + changeInfoSeq.source.startAddressM + '</td>' +
            '<td class="project-change-table-data-cell">' + changeInfoSeq.source.endRoadPartNumber + '</td>' +
            '<td class="project-change-table-data-cell">' + changeInfoSeq.source.endAddressM + '</td>' +
            '<td class="project-change-table-data-cell">' + changeInfoSeq.target.roadNumber + '</td>' +
            '<td class="project-change-table-data-cell">' + changeInfoSeq.target.trackCode + '</td>' +
            '<td class="project-change-table-data-cell">' + changeInfoSeq.target.startRoadPartNumber + '</td>' +
            '<td class="project-change-table-data-cell">' + changeInfoSeq.target.startAddressM + '</td>' +
            '<td class="project-change-table-data-cell">' + changeInfoSeq.target.endRoadPartNumber + '</td>' +
            '<td class="project-change-table-data-cell">' + changeInfoSeq.target.endAddressM + '</td>' +
            '<td class="project-change-table-data-cell">' + changeInfoSeq.discontinuity + '</td>' +
            '<td class="project-change-table-data-cell">' + linkForm.getRoadType(changeInfoSeq.roadType) + '</td>' +
            '<td class="project-change-table-data-cell">' + projectChangeData.ely + '</td>' +
            '</tr>';
        });
        htmlTable += '</table>';

        $('.project-changes').html($(htmlTable));
      });

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