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
    var unchangedStatus = 1;
    var newLinkStatus = 2;
    var transferredLinkStatus = 3;
    var numberingLinkStatus = 4;
    var terminatedLinkStatus = 5;

    var changeTable =
      $('<div class="change-table-frame"></div>');
    // Text about validation success hard-coded now
    // TODO: handle status-text for real
    // TODO: table not responsive
    changeTable.append('<div class="change-table-header">Validointi ok. Alla näet muutokset projektissa.</div>');
    changeTable.append('<button class="close wbtn-close">Sulje <span>X</span></button>');
    changeTable.append('<button class="max wbtn-max"><span id="buttonText">Suurenna </span><span id="sizeSymbol" style="font-size: 175%;font-weight: 900;">□</span></button>');
    changeTable.append('<div class="change-table-borders">' +
      '<div id ="change-table-borders-changetype"></div>' +
      '<div id ="change-table-borders-source"></div>' +
      '<div id ="change-table-borders-reversed"></div>' +
      '<div id ="change-table-borders-target"></div></div>');
    changeTable.append('<div class="change-table-sections">' +
      '<label class="change-table-heading-label" id="label-type">Ilmoitus</label>' +
      '<label class="change-table-heading-label" id="label-source">Nykyosoite</label>' +
      '<label class="change-table-heading-label" id="label-reverse"></label>' +
      '<label class="change-table-heading-label" id="label-target">Uusi osoite</label>');
    changeTable.append('<div class="change-table-dimension-headers">' +
      '<table class="change-table-dimensions">' +
      '<tr>' +
      '<td class="project-change-table-dimension-first-h"></td>' +
      '<td class="project-change-table-dimension">TIE</td>' +
      '<td class="project-change-table-dimension">AJR</td>' +
      '<td class="project-change-table-dimension">AOSA</td>' +
      '<td class="project-change-table-dimension">AET</td>' +
      '<td class="project-change-table-dimension">LOSA</td>' +
      '<td class="project-change-table-dimension">LET</td>' +
      '<td class="project-change-table-dimension">JATKUU</td>' +
      '<td class="project-change-table-dimension dimension-road-type">TIETYYPPI</td>' +
      '<td class="project-change-table-dimension">ELY</td>' +
      '<td class="project-change-table-dimension">KÄÄNTO</td>' +
      '<td class="project-change-table-dimension">TIE</td>' +
      '<td class="project-change-table-dimension">AJR</td>' +
      '<td class="project-change-table-dimension">AOSA</td>' +
      '<td class="project-change-table-dimension">AET</td>' +
      '<td class="project-change-table-dimension">LOSA</td>' +
      '<td class="project-change-table-dimension">LET</td>' +
      '<td class="project-change-table-dimension">JATKUU</td>' +
      '<td class="project-change-table-dimension dimension-road-type">TIETYYPPI</td>' +
      '<td class="project-change-table-dimension dimension-last">ELY</td>' +
      '</tr>' +
      '</table>' +
      '</div>');
    changeTable.append('<div class="project-changes"></div>');

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
        var htmlTable ='<table class="change-table">';
        if(!_.isUndefined(projectChangeData) && projectChangeData !== null){
          _.each(projectChangeData.changeInfoSeq, function(changeInfoSeq) {
            if (changeInfoSeq.changetype === newLinkStatus) {
              htmlTable += '<tr class="change-table-data-row">';
              htmlTable += getEmptySource(changeInfoSeq);
              htmlTable += getReversed(changeInfoSeq);
              htmlTable += getTargetInfo(changeInfoSeq);
              htmlTable += '</tr>';
            } else if (changeInfoSeq.changetype === terminatedLinkStatus) {
              htmlTable += '<tr class="change-table-data-row">';
              htmlTable += getSourceInfo(changeInfoSeq);
              htmlTable += getReversed(changeInfoSeq);
              htmlTable += getEmptyTarget();
              htmlTable += '</tr>';
            } else if (changeInfoSeq.changetype === unchangedStatus) {
              htmlTable += '<tr class="change-table-data-row">';
              htmlTable += getSourceInfo(changeInfoSeq);
              htmlTable += getReversed(changeInfoSeq);
              htmlTable += getTargetInfo(changeInfoSeq);
              htmlTable += '</tr>';
            } else if (changeInfoSeq.changetype === transferredLinkStatus) {
              htmlTable += '<tr class="change-table-data-row">';
              htmlTable += getSourceInfo(changeInfoSeq);
              htmlTable += getReversed(changeInfoSeq);
              htmlTable += getTargetInfo(changeInfoSeq);
              htmlTable += '</tr>';
            } else if (changeInfoSeq.changetype === numberingLinkStatus) {
              htmlTable += '<tr class="change-table-data-row">';
              htmlTable += getSourceInfo(changeInfoSeq);
              htmlTable += getReversed(changeInfoSeq);
              htmlTable += getTargetInfo(changeInfoSeq);
              htmlTable += '</tr>';
            }
          });
        }
        htmlTable += '</table>';
        $('.project-changes').html($(htmlTable));
      });

      changeTable.on('click', 'button.close', function (){
        $('.project-changes').height('110px');
        $('[id=change-table-borders-target]').height('180px');
        $('[id=change-table-borders-reverse]').height('180px');
        $('[id=change-table-borders-source]').height('180px');
        $('[id=change-table-borders-changetype]').height('180px');
        $('#information-content').empty();
        $('#send-button').attr('disabled', true);
        hide();
      });
    }

    function getReversed(changeInfoSeq){
      return ((changeInfoSeq.reversed) ? '<td class="project-change-table-data-cell">&#9745</td>': '<td class="project-change-table-data-cell">&#9744</td>');
    }

    function getEmptySource(changeInfoSeq) {
      return '<td class="project-change-table-dimension-first">' + getChangeType(changeInfoSeq.changetype) + '</td>' +
        '<td class="project-change-table-data-cell"></td>' +
        '<td class="project-change-table-data-cell"></td>' +
        '<td class="project-change-table-data-cell"></td>' +
        '<td class="project-change-table-data-cell"></td>' +
        '<td class="project-change-table-data-cell"></td>' +
        '<td class="project-change-table-data-cell"></td>' +
        '<td class="project-change-table-data-cell"></td>' +
        '<td class="project-change-table-data-cell data-cell-road-type"></td>' +
        '<td class="project-change-table-data-cell"></td>';
    }
    function getEmptyTarget() {
      return'<td class="project-change-table-data-cell"></td>' +
        '<td class="project-change-table-data-cell"></td>' +
        '<td class="project-change-table-data-cell"></td>' +
        '<td class="project-change-table-data-cell"></td>' +
        '<td class="project-change-table-data-cell"></td>' +
        '<td class="project-change-table-data-cell"></td>' +
        '<td class="project-change-table-data-cell"></td>' +
        '<td class="project-change-table-data-cell data-cell-road-type"></td>' +
        '<td class="project-change-table-data-cell"></td>';
    }

    function getTargetInfo(changeInfoSeq)
    {
      return '<td class="project-change-table-data-cell">' + changeInfoSeq.target.roadNumber + '</td>'+
        '<td class="project-change-table-data-cell">' + changeInfoSeq.target.trackCode + '</td>' +
        '<td class="project-change-table-data-cell">' + changeInfoSeq.target.startRoadPartNumber + '</td>' +
        '<td class="project-change-table-data-cell">' + changeInfoSeq.target.startAddressM + '</td>' +
        '<td class="project-change-table-data-cell">' + changeInfoSeq.target.endRoadPartNumber + '</td>' +
        '<td class="project-change-table-data-cell">' + changeInfoSeq.target.endAddressM + '</td>' +
        '<td class="project-change-table-data-cell">' + changeInfoSeq.target.discontinuity + '</td>' +
        '<td class="project-change-table-data-cell data-cell-road-type">'+ changeInfoSeq.target.roadType + '</td>' +
        '<td class="project-change-table-data-cell">' + changeInfoSeq.target.ely + '</td>';
    }

    function getSourceInfo(changeInfoSeq)
    {
      return '<tr class="change-table-data-row">' +
        '<td class="project-change-table-dimension-first">' + getChangeType(changeInfoSeq.changetype) + '</td>' +
        '<td class="project-change-table-data-cell">' + changeInfoSeq.source.roadNumber + '</td>' +
        '<td class="project-change-table-data-cell">' + changeInfoSeq.source.trackCode + '</td>' +
        '<td class="project-change-table-data-cell">' + changeInfoSeq.source.startRoadPartNumber + '</td>' +
        '<td class="project-change-table-data-cell">' + changeInfoSeq.source.startAddressM + '</td>' +
        '<td class="project-change-table-data-cell">' + changeInfoSeq.source.endRoadPartNumber + '</td>' +
        '<td class="project-change-table-data-cell">' + changeInfoSeq.source.endAddressM + '</td>' +
        '<td class="project-change-table-data-cell">' + changeInfoSeq.source.discontinuity + '</td>' +
        '<td class="project-change-table-data-cell data-cell-road-type">' + changeInfoSeq.source.roadType + '</td>' +
        '<td class="project-change-table-data-cell">' + changeInfoSeq.source.ely + '</td>';
    }

    var windowMaximized = false;
    changeTable.on('click', 'button.max', function (){
      if(windowMaximized) {
        $('.change-table-frame').height('260px');
        $('.project-changes').height('110px');
        $('[id=change-table-borders-target]').height('180px');
        $('[id=change-table-borders-source]').height('180px');
        $('[id=change-table-borders-reversed]').height('180px');
        $('[id=change-table-borders-changetype]').height('180px');
        $('[id=buttonText]').text("Suurenna ");
        $('[id=sizeSymbol]').text("□");
        windowMaximized=false;
      } else {
        $('.change-table-frame').height('80%');
        $('.project-changes').height('560px');
        $('[id=change-table-borders-target]').height('670px');
        $('[id=change-table-borders-source]').height('670px');
        $('[id=change-table-borders-reversed]').height('670px');
        $('[id=change-table-borders-changetype]').height('670px');
        $('[id=buttonText]').text("Pienennä ");
        $('[id=sizeSymbol]').text("_");
        windowMaximized=true;
      }
    });

    eventbus.on('projectChangeTable:refresh', function() {
      bindEvents();
      getChanges();
    });

    return{
      show: show,
      hide: hide,
      bindEvents: bindEvents
    };
  };
})(this);
