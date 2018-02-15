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
    var LinkStatus = LinkValues.LinkStatus;
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
      enableTableInteractions();

    }

    function hide() {
      resetInteractions();
      $('#information-content').empty();
      $('#send-button').attr('disabled', true);
      changeTable.hide();
    }

    function resetInteractions() {
      $('.change-table-frame')[0].setAttribute('data-x', 0);
      $('.change-table-frame')[0].setAttribute('data-y', 0);
      $('.change-table-frame').css('transform', 'none');
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
      eventbus.once('projectChanges:fetched', function(projectChangeData) {
        var htmlTable = "";
        if (!_.isUndefined(projectChangeData) && projectChangeData !== null && !_.isUndefined(projectChangeData.changeTable) && projectChangeData.changeTable !== null) {
          _.each(projectChangeData.changeTable.changeInfoSeq, function (changeInfoSeq) {
            if (changeInfoSeq.changetype === LinkStatus.New.value) {
              htmlTable += '<tr class="row-changes">';
              htmlTable += getEmptySource(changeInfoSeq);
              htmlTable += getReversed(changeInfoSeq);
              htmlTable += getTargetInfo(changeInfoSeq);
              htmlTable += '</tr>';
            } else if (changeInfoSeq.changetype === LinkStatus.Terminated.value) {
              htmlTable += '<tr class="row-changes">';
              htmlTable += getSourceInfo(changeInfoSeq);
              htmlTable += getReversed(changeInfoSeq);
              htmlTable += getEmptyTarget();
              htmlTable += '</tr>';
            } else if (changeInfoSeq.changetype === LinkStatus.Unchanged.value) {
              htmlTable += '<tr class="row-changes">';
              htmlTable += getSourceInfo(changeInfoSeq);
              htmlTable += getReversed(changeInfoSeq);
              htmlTable += getTargetInfo(changeInfoSeq);
              htmlTable += '</tr>';
            } else if (changeInfoSeq.changetype === LinkStatus.Transfer.value) {
              htmlTable += '<tr class="row-changes">';
              htmlTable += getSourceInfo(changeInfoSeq);
              htmlTable += getReversed(changeInfoSeq);
              htmlTable += getTargetInfo(changeInfoSeq);
              htmlTable += '</tr>';
            } else if (changeInfoSeq.changetype === LinkStatus.Numbering.value) {
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
        if (projectChangeData.validationErrors.length === 0){
          $('.change-table-header').html($('<div>Validointi ok. Alla näet muutokset projektissa.</div>'));
          if($('.change-table-frame').css('display')==="block")
            $('#send-button').attr('disabled',false); //enables send button if changetable is open
        }
        else
        {
          $('.change-table-header').html($('<div><font color="yellow">Tarkista validointitulokset. Yhteenvetotaulukko voi olla puutteellinen.</font></div>'));
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
        '<td class="project-change-table-dimension">' + (changeInfoSeq.target.endAddressM - changeInfoSeq.target.startAddressM) + '</td>' +
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
        '<td class="project-change-table-dimension">' + (changeInfoSeq.source.endAddressM - changeInfoSeq.source.startAddressM) + '</td>' +
        '<td class="project-change-table-dimension">' + changeInfoSeq.source.discontinuity + '</td>' +
        '<td class="project-change-table-dimension">' + changeInfoSeq.source.roadType + '</td>' +
        '<td class="project-change-table-dimension">' + changeInfoSeq.source.ely + '</td>';
    }

    function dragListener (event) {
      var target = event.target,
        x = (parseFloat(target.getAttribute('data-x')) || 0) + event.dx,
        y = (parseFloat(target.getAttribute('data-y')) || 0) + event.dy;
      target.style.webkitTransform =
        target.style.transform =
          'translate(' + x + 'px, ' + y + 'px)';
      target.setAttribute('data-x', x);
      target.setAttribute('data-y', y);
    }


    function enableTableInteractions() {
      interact('.change-table-frame')
        .draggable({
          onmove: dragListener,
          restrict: {
            restriction: '.container',
            elementRect: { top: 0, left: 0, bottom: 1, right: 1 }
          }
        })
        .resizable({
          edges: { left: true, right: true, bottom: true, top: true },
          restrictEdges: {
            outer: '.container',
            endOnly: true
          },
          restrictSize: {
            min: { width: 100, height: 50 }
          },
          inertia: true
        })
        .on('resizemove', function (event) {
          var target = event.target,
            x = (parseFloat(target.getAttribute('data-x')) || 0),
            y = (parseFloat(target.getAttribute('data-y')) || 0);
          target.style.width  = event.rect.width + 'px';
          target.style.height = event.rect.height + 'px';
          x += event.deltaRect.left;
          y += event.deltaRect.top;
          target.style.webkitTransform = target.style.transform =
            'translate(' + x + 'px,' + y + 'px)';
          target.setAttribute('data-x', x);
          target.setAttribute('data-y', y);
        });
    }

    changeTable.on('click', 'button.max', function (){
      resetInteractions();
      if(windowMaximized) {
        $('.change-table-frame').height('260px');
        $('.change-table-frame').width('1135px');
        $('.change-table-frame').css('top', '620px');
        $('[id=change-table-borders-target]').height('180px');
        $('[id=change-table-borders-source]').height('180px');
        $('[id=change-table-borders-reversed]').height('180px');
        $('[id=change-table-borders-changetype]').height('180px');
        $('[id=buttonText]').text("Suurenna ");
        $('[id=sizeSymbol]').text("□");
        windowMaximized=false;
      } else {
        $('.change-table-frame').height('800px');
        $('.change-table-frame').width('1135px');
        $('.change-table-frame').css('top', '50px');
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
      enableTableInteractions();
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
