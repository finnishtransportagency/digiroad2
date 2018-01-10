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
    var windowMaximized = false;

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
      '<td class="project-change-table-dimension-h">TIE</td>' +
      '<td class="project-change-table-dimension-h">AJR</td>' +
      '<td class="project-change-table-dimension-h">OSA</td>' +
      '<td class="project-change-table-dimension-h">AET</td>' +
      '<td class="project-change-table-dimension-h">LET</td>' +
      '<td class="project-change-table-dimension-h">PIT</td>' +
      '<td class="project-change-table-dimension-h">JATK</td>' +
      '<td class="project-change-table-dimension-h dimension-road-type">TIETY</td>' +
      '<td class="project-change-table-dimension-h">ELY</td>' +
      '<td class="project-change-table-dimension-h dimension-reversed">&nbsp;KÄÄNTÖ</td>' +
      '<td class="project-change-table-dimension-h">TIE</td>' +
      '<td class="project-change-table-dimension-h">AJR</td>' +
      '<td class="project-change-table-dimension-h">OSA</td>' +
      '<td class="project-change-table-dimension-h">AET</td>' +
      '<td class="project-change-table-dimension-h">LET</td>' +
      '<td class="project-change-table-dimension-h">PIT</td>' +
      '<td class="project-change-table-dimension-h">JATK</td>' +
      '<td class="project-change-table-dimension-h dimension-road-type-h">TIETY</td>' +
      '<td class="project-change-table-dimension-h dimension-last-h">ELY</td>' +
      '</tr>' +
      '</table>' +
      '</div>');

    function show(){
      $('.container').append(changeTable.toggle());
      bindEvents();
      getChanges();
    }

    function hide() {
      $('#information-content').empty();
      $('#send-button').attr('disabled', true);
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
      $('.row-changes').remove();
      eventbus.once('projectChanges:fetched', function(projectChangeData){
        var htmlTable ="";
        if(!_.isUndefined(projectChangeData) && projectChangeData !== null){
          _.each(projectChangeData.changeTable.changeInfoSeq, function(changeInfoSeq) {
            if (changeInfoSeq.changetype === newLinkStatus) {
              htmlTable += '<tr class="row-changes">';
              htmlTable += getEmptySource(changeInfoSeq);
              htmlTable += getReversed(changeInfoSeq);
              htmlTable += getTargetInfo(changeInfoSeq);
              htmlTable += '</tr>';
            } else if (changeInfoSeq.changetype === terminatedLinkStatus) {
              htmlTable += '<tr class="row-changes">';
              htmlTable += getSourceInfo(changeInfoSeq);
              htmlTable += getReversed(changeInfoSeq);
              htmlTable += getEmptyTarget();
              htmlTable += '</tr>';
            } else if (changeInfoSeq.changetype === unchangedStatus) {
              htmlTable += '<tr class="row-changes">';
              htmlTable += getSourceInfo(changeInfoSeq);
              htmlTable += getReversed(changeInfoSeq);
              htmlTable += getTargetInfo(changeInfoSeq);
              htmlTable += '</tr>';
            } else if (changeInfoSeq.changetype === transferredLinkStatus) {
              htmlTable += '<tr class="row-changes">';
              htmlTable += getSourceInfo(changeInfoSeq);
              htmlTable += getReversed(changeInfoSeq);
              htmlTable += getTargetInfo(changeInfoSeq);
              htmlTable += '</tr>';
            } else if (changeInfoSeq.changetype === numberingLinkStatus) {
              htmlTable += '<tr class="row-changes">';
              htmlTable += getSourceInfo(changeInfoSeq);
              htmlTable += getReversed(changeInfoSeq);
              htmlTable += getTargetInfo(changeInfoSeq);
              htmlTable += '</tr>';
            }
          });
        }
        $('.row-changes').remove();
        $('.change-table-dimensions').append($(htmlTable));
        if (projectChangeData.validationErrors.length===0)
          $('.change-table-header').html($('<div>Validointi ok. Alla näet muutokset projektissa.</div>'));
        else
        {
          $('.change-table-header').html($('<div>Tarkista validointitulokset. Yhteenvetotaulukko voi olla puutteellinen.</div>'));
        }
      });

      changeTable.on('click', 'button.close', function (){
        hide();
      });
    }

    function getReversed(changeInfoSeq){
      return ((changeInfoSeq.reversed) ? '<td class="project-change-table-dimension">&#9745</td>': '<td class="project-change-table-dimension">&#9744</td>');
    }

    function getEmptySource(changeInfoSeq) {
      return '<td class="project-change-table-dimension-first">' + getChangeType(changeInfoSeq.changetype) + '</td>' +
        '<td class="project-change-table-dimension"></td>' +
        '<td class="project-change-table-dimension"></td>' +
        '<td class="project-change-table-dimension"></td>' +
        '<td class="project-change-table-dimension"></td>' +
        '<td class="project-change-table-dimension"></td>' +
        '<td class="project-change-table-dimension"></td>' +
        '<td class="project-change-table-dimension"></td>' +
        '<td class="project-change-table-dimension"></td>' +
        '<td class="project-change-table-dimension"></td>';
    }
    function getEmptyTarget() {
      return '<td class="project-change-table-dimension"></td>' +
        '<td class="project-change-table-dimension"></td>' +
        '<td class="project-change-table-dimension"></td>' +
        '<td class="project-change-table-dimension"></td>' +
        '<td class="project-change-table-dimension"></td>' +
        '<td class="project-change-table-dimension"></td>' +
        '<td class="project-change-table-dimension"></td>' +
        '<td class="project-change-table-dimension"></td>' +
        '<td class="project-change-table-dimension"></td>';
    }

    function getTargetInfo(changeInfoSeq){
      return '<td class="project-change-table-dimension">' + changeInfoSeq.target.roadNumber + '</td>'+
        '<td class="project-change-table-dimension">' + changeInfoSeq.target.trackCode + '</td>' +
        '<td class="project-change-table-dimension">' + changeInfoSeq.target.startRoadPartNumber + '</td>' +
        '<td class="project-change-table-dimension">' + changeInfoSeq.target.startAddressM + '</td>' +
        '<td class="project-change-table-dimension">' + changeInfoSeq.target.endAddressM + '</td>' +
        '<td class="project-change-table-dimension">' + changeInfoSeq.target.endAddressM + '</td>' +
        '<td class="project-change-table-dimension">' + changeInfoSeq.target.discontinuity + '</td>' +
        '<td class="project-change-table-dimension">'+ changeInfoSeq.target.roadType + '</td>' +
        '<td class="project-change-table-dimension">' + changeInfoSeq.target.ely + '</td>';
    }

    function getSourceInfo(changeInfoSeq){
      return '<td class="project-change-table-dimension-first">' + getChangeType(changeInfoSeq.changetype) + '</td>' +
        '<td class="project-change-table-dimension">' + changeInfoSeq.source.roadNumber + '</td>' +
        '<td class="project-change-table-dimension">' + changeInfoSeq.source.trackCode + '</td>' +
        '<td class="project-change-table-dimension">' + changeInfoSeq.source.startRoadPartNumber + '</td>' +
        '<td class="project-change-table-dimension">' + changeInfoSeq.source.startAddressM + '</td>' +
        '<td class="project-change-table-dimension">' + changeInfoSeq.source.endAddressM + '</td>' +
        '<td class="project-change-table-dimension">' + changeInfoSeq.source.endAddressM + '</td>' +
        '<td class="project-change-table-dimension">' + changeInfoSeq.source.discontinuity + '</td>' +
        '<td class="project-change-table-dimension">' + changeInfoSeq.source.roadType + '</td>' +
        '<td class="project-change-table-dimension">' + changeInfoSeq.source.ely + '</td>';
    }

    changeTable.on('click', 'button.max', function (){
      if(windowMaximized) {
        $('.change-table-frame').height('260px');
        $('[id=change-table-borders-target]').height('180px');
        $('[id=change-table-borders-source]').height('180px');
        $('[id=change-table-borders-reversed]').height('180px');
        $('[id=change-table-borders-changetype]').height('180px');
        $('[id=buttonText]').text("Suurenna ");
        $('[id=sizeSymbol]').text("□");
        windowMaximized=false;
      } else {
        $('.change-table-frame').height('80%');
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

    eventbus.on('projectChangeTable:hide', function() {
      hide();
    });

    return{
      show: show,
      hide: hide,
      bindEvents: bindEvents
    };
  };
})(this);
