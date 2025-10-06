(function (root) {
  root.CsvReportsWorkList = function () {
    WorkListView.call(this);
    var me = this;
    this.title = 'Raportointityökalu';
    var backend;
    var municipalities;
    var assetsList;
    var refresh;

    var assetExtraOptions = {
      allAssets: { id: "1", name: "Kaikki tietolajit" },
      allPoints: { id: "2", name: "Pistemäiset tietolajit" },
      allLinears: { id: "3", name: "Viivamaiset tietolajit" }
    };

    var municipalityExtraOptions = {
      allMunicipalities: { id: "1000", name: "Kaikki kunnat" }
    };

    this.initialize = function (mapBackend) {
      backend = mapBackend;
      me.bindEvents();
    };


    this.bindEvents = function () {
      eventbus.on('csvReports:select', function (listP) {
        $('.container').hide();
        $('#work-list').show();
        $('body').addClass('scrollable');
        me.generateWorkList(listP);
      });

    };

    function getMunicipalities() {
      if (_.isEmpty($("#municipalities_search").find('option'))) {
        if (_.isEmpty(municipalities)) {

          backend.getMunicipalities().then(function (result) {
            municipalities = result;
            setMunicipalities();
          });

        } else
          setMunicipalities();
      }
    }

    function getAssetsType() {
      if (_.isEmpty($("#assets_search").find('option'))) {
        if (_.isEmpty(assetsList)) {

          backend.getAssetTypes().then(function (result) {
            assetsList = result;
            setAssets();
          });
        } else
          setAssets();
      }
    }

    function setMunicipalities() {
      var allMunicipalityOptions = _.concat([municipalityExtraOptions.allMunicipalities], municipalities);

      _.forEach(allMunicipalityOptions, function (municipality) {
        $('#municipalities_search').append($('<option>', {
          value: municipality.id,
          text: municipality.name
        }));
      });
    }


    function setAssets() {
      var topOptions = [assetExtraOptions.allAssets, assetExtraOptions.allPoints, assetExtraOptions.allLinears];
      var allAssetOptions = _.concat(topOptions, assetsList);

      _.forEach(allAssetOptions, function (asset) {
        $('#assets_search').append($('<option>', {
          value: asset.id,
          text: asset.name
        }));
      });
    }


    function populatedMultiSelectBoxes() {
      getMunicipalities();
      getAssetsType();
    }

    function municipalitySort(a, b){
      var allMunicipalitiesId = municipalityExtraOptions.allMunicipalities.id;

      if (a.value == allMunicipalitiesId)
        return -1;
      else if ( b.value == allMunicipalitiesId)
        return 1;

      return a.text > b.text ? 1 : -1;
    }

    function assetTypeSort(a, b){
      var topElems = [assetExtraOptions.allAssets.id, assetExtraOptions.allLinears.id, assetExtraOptions.allPoints.id];

      if (_.includes(topElems, a.value) && _.includes(topElems, b.value))
        return a.value > b.value ? 1 : -1;
      else if (_.includes(topElems, a.value))
        return -1;
      else if (_.includes(topElems, b.value))
        return 1;

      return  a.text > b.text ? 1 : -1;
    }

    function multiSelectBoxForMunicipalities() {
      $('#municipalities_search').multiselect({
        search: {
          left:
              '<label class="control-label labelBoxLeft">Kaikki kunnat</label>' +
              '<input type="text" id = "left_municipalities" class="form-control" placeholder="Kuntanimi" />',
          right:
              '<label class="control-label labelBoxRight">Valitut Kunnat</label>' +
              '<input type="text" id = "right_municipalities" class="form-control" placeholder="Kuntanimi" />'
        },
        sort:  {
          left: function (a, b) {
            return municipalitySort(a, b);
          },
          right:   function (a, b) {
            return municipalitySort(a, b);
          }
        },
        fireSearch: function (value) {
          return value.length >= 1;
        }
      });
    }

    function multiSelectBoxForAssets() {
      $('#assets_search').multiselect({
        search: {
          left:
              '<label class="control-label labelBoxLeft">Kaikki Tietolajit</label>' +
              '<input type="text" id = "left_assets" class="form-control" placeholder="Tietolajit Nimi" />',
          right:
              '<label class="control-label labelBoxRight">Valitut Tietolajit</label>' +
              '<input type="text" id = "right_assets" class="form-control" placeholder="Tietolajit Nimi" />'
        },
        sort: {
          left: function (a, b) {
            return assetTypeSort(a, b);
          },
          right:   function (a, b) {
            return assetTypeSort(a, b);
          }
        },
        fireSearch: function (value) {
          return value.length >= 1;
        }
      });
    }

    function saveForm() {
      var formData = {};

      var municipalityValues = $("#municipalities_search_to, select[name*='municipalityNumbers']").find('option');
      formData.municipalityNumbers = _.map(municipalityValues, function (municipality) {
         return municipality.value;
      });

      var assetValues = $("#assets_search_to, select[name*='assetNumbers']").find('option');
      formData.assetNumbers = _.map(assetValues, function (asset) {
         return asset.value;
      });

      me.addSpinner();
      backend.postGenerateCsvReport(formData.municipalityNumbers, formData.assetNumbers,
          function(data) {
            me.removeSpinner();
            addNewRow(data);
          },
          function(xhr) {
            me.removeSpinner();
            if(xhr.status === 403)
              alert("Vain operaattori voi suorittaa Excel-ajon");
            addNewRow(xhr.responseText);
          });
    }

    this.generateWorkList = function (listP) {
      me.addSpinner();
      listP.then(function (result) {

        $('#work-list').html('' +
            '<div style="overflow: auto;">' +
              '<div class="page">' +
                '<div class="content-box">' +
                  '<header id="work-list-header">' + me.title +
                    '<a class="header-link" href="#' + window.applicationModel.getSelectedLayer() + '">Sulje</a>' +
                  '</header>' +
                  '<div class="work-list">' +
                  '</div>' +
              '</div>' +
            '</div>'
        );

        $('#work-list .work-list').html(me.workListItemTable());

        populatedMultiSelectBoxes();

        multiSelectBoxForMunicipalities();
        multiSelectBoxForAssets();

        $('#send_btn').click( saveForm );

       $('#municipalities_search_rightSelected, #municipalities_search_leftSelected, #assets_search_rightSelected, #assets_search_leftSelected').on('click', enableSubmitButton);
       $('#municipalities_search, #municipalities_search_to, #assets_search, #assets_search_to').on('dblclick', enableSubmitButton);

        me.getJobs();
        me.removeSpinner();
      });
    };



    function enableSubmitButton() {
      var isMunicipalityEmpty = _.isEmpty($("#municipalities_search_to, select[name*='municipalityNumbers']").find('option'));
      var isAssetEmpty = _.isEmpty($("#assets_search_to, select[name*='assetNumbers']").find('option'));

      var disableStatus = isAssetEmpty || isMunicipalityEmpty;
      $('.btn.btn-primary.btn-lg').prop('disabled', disableStatus);
    }


    this.workListItemTable = function() {

      var form =
          '<div class="form form-horizontal" id="csvExport" role="form" >' +
          '<div class="form-group multiSelectItems">' +
              '<div class="form-group" disabled="disabled" >' +
                '<div class="row">' +
                  '<div class="col-xs-5">' +
                    '<select id="municipalities_search" class="form-control" multiple="multiple">' +
                    '</select>' +
                  '</div>' +
                  '<div class="col-xs-2">' +
                    '<button type="button" id="municipalities_search_rightSelected" class="action-mode-btn btn btn-block edit-mode-btn btn-primary"> &gt; </button>' +
                    '<button type="button" id="municipalities_search_leftSelected" class="action-mode-btn btn btn-block edit-mode-btn btn-primary"> &lt; </button>' +
                  '</div>' +
                  '<div class="col-xs-5">' +
                    '<select name="municipalityNumbers" id="municipalities_search_to" class="form-control" multiple="multiple">' +
                    '</select>' +
                  '</div>' +
                '</div>' +
              '</div>' +
            '</div>' +
            '<div class="form-group multiSelectItems">' +
              '<div class="form-group" disabled="disabled" >' +
                '<div class="row">' +
                  '<div class="col-xs-5">' +
                    '<select id="assets_search" class="form-control" multiple="multiple">' +
                    '</select>' +
                  '</div>' +
                  '<div class="col-xs-2">' +
                    '<button type="button" id="assets_search_rightSelected" class="action-mode-btn btn btn-block edit-mode-btn btn-primary"> &gt; </button>' +
                    '<button type="button" id="assets_search_leftSelected" class="action-mode-btn btn btn-block edit-mode-btn btn-primary"> &lt; </button>' +
                  '</div>' +
                  '<div class="col-xs-5">' +
                    '<select name="assetNumbers" id="assets_search_to" class="form-control" multiple="multiple">' +
                    '</select>' +
                  '</div>' +
                '</div>' +
              '</div>' +
            '</div>' +
            '<div class="form-controls" style="text-align: right;">' +
              '<button id="send_btn" class="btn btn-primary btn-lg" disabled="disabled">Luo raportti</button>' +
            '</div>' +
          '</div>'+
          '<div class="job-status">' +
          '</div>'+
          '<div class="job-content">'+
          '</div>';

      return form;
    };


    var refreshJobs = function() {
      var jobsInProgress = $('.in-progress').map(function(){
        return $(this).attr('id');
      });

      if(_.isEmpty(jobsInProgress)) {
        clearInterval(refresh);
        refresh = null;
      }
      else {
        backend.getExportsJobsByIds(jobsInProgress.toArray()).then(function (jobs) {
          var endedJobs = _.filter(jobs, function (job) {
            return job.status !== 1;
          });
          _.map(endedJobs, replaceRow);
        });
      }

    };

    function downloadCsv(event){
      var id = $(event.currentTarget).prop('id');

      backend.getCsvReport(id).then(function(info){
        var csvFile = new Blob(["\ufeff", info.content], {type: "text/csv;charset=ANSI"});

        var auxElem = $('<a></a>').attr("id","tmp_csv_dwn").attr("href",window.URL.createObjectURL(csvFile)).attr("download", info.fileName);
        auxElem[0].click(); /*trigger href*/
      });
    }

    function replaceRow(job) {
      if (!_.isEmpty(job)) {
        var newRow = jobRow(job);
        $("#"+job.id).replaceWith(newRow);

        $(".job-status-table").find('.job-status-link#' + job.id).on('click', function (event) {
          getJob(event);
        });

        $(".job-status-table").find('.btn-download#' + job.id).on('click', function (event) {
          downloadCsv(event);
        });

        bindTableViews(job);
      }
    }


    function getJob(evt){
      var id = $(evt.currentTarget).prop('id');
      backend.getExportJob(id).then(function(job){
        GenericConfirmPopup(buildJobView(job), {type: "alert", container: "#work-list"});
      });
    }

    this.getJobs = function () {
      backend.getExportJobsByUser().then(function(jobs){
        if(!_.isEmpty(jobs))
          $('.job-status').empty().html(buildJobTable(jobs));

        _.forEach(jobs, function(job) {
          if (job.status > 2){
            $(".job-status-table").find('.job-status-link#' + job.id).on('click', function (event) {
              getJob(event);
            });
          }
          else if ( job.status == 2) {
            $(".job-status-table").find('.btn-download#' + job.id).on('click', function (event) {
              downloadCsv(event);
            });
          }
          bindTableViews(job);
        });

        refresh = setInterval(refreshJobs, 3000);
      });
    };

    var bindTableViews = function (job) {
      $("#viewAssets"+job.id).on('click', function () {
        GenericConfirmPopup(convertToListItem("Tietolajit", job.exportedAssets), {type: "alert", container: "#work-list"});
      });

      $("#viewMunicipalities"+job.id).on('click', function () {
        GenericConfirmPopup(convertToListItem("Kunnat", job.municipalities), {type: "alert", container: "#work-list"});
      });
    };

    var buildJobView = function(job) {
      return  '<header>' +
                '<h2> CSV-eräajon virhetilanteet: ' + job.fileName + '</h2>' +
              '</header>' +
              '<div class="bigListPopUp">' + job.content + '</div>';
    };

    function addNewRow(job) {
      if (!_.isEmpty(job)) {
        var newRow = jobRow(job);
        var table = $(".job-status-table tbody tr:first");

        if(_.isEmpty(table))
          $('.job-status').empty().html(buildJobTable([job]));
        else
          table.before(newRow);

        bindTableViews(job);

        if(!refresh)
          refresh = setInterval(refreshJobs, 3000);
      }
    }

    var buildJobTable = function(jobs) {
      var table = function (jobs) {
        return $('<table>').addClass('job-status-table')
            .append(tableHeaderRow())
            .append(tableBodyRows(jobs));
      };

      var tableHeaderRow = function () {
        return ''+
          '<thead>'+
              '<tr>'+
                '<th id="date" class="csvReportDate">Päivämäärä</th>'+
                '<th id="file" class="csvReportFile"">Tiedosto</th>'+
                '<th id="exportedAssets" class="csvReportExportedAssets">Tietolajit</th>'+
                '<th id="municipalities" class="csvReportMunicipalities">Kunnat</th>'+
                '<th id="status" class="csvReportStatus">Tila</th>'+
                '<th id="detail" class="csvReportDetail">Raportti</th>'+
            '</tr>'+
          '</thead>';
      };

      var tableBodyRows = function (jobs) {
        return $('<tbody>').attr("id",'tblCsvReport').append(tableContentRows(jobs));
      };

      var tableContentRows = function (jobs) {
        return _.map(jobs, function (job) {
          return jobRow(job).concat('');
        });
      };

      return table(jobs);
    };

    var jobRow = function (job) {
      var btnToDetail = "";

      if (job.status > 2){
        btnToDetail = '<button class="btn btn-block btn-primary job-status-link" id="'+ job.id + '">Avaa</button>';
      }
      else if ( job.status == 2)
        btnToDetail = '<a id="' + job.id + '" class="btn btn-primary btn-download">Lataa CSV<img src="images/icons/export-icon.png"/></a>';

      return '' +
          '<tr class="' + (job.status === 1 ? 'in-progress' : '') + '" id="' + job.id + '">' +
            '<td headers="date" class="csvReportDate">' + job.createdDate + '</td>' +
            '<td headers="file" class="csvReportFile" id="file">' + job.fileName + '</td>' +
            '<td headers="exportedAssets" class="csvReportExportedAssets">'+
              '<a id="viewAssets' + job.id + '" class="selectable">Näytä</a>' +
            '</td>' +
            '<td headers="municipalities" class="csvReportMunicipalities">'+
              '<a id="viewMunicipalities' + job.id + '" class="selectable">Näytä</a>' +
            '</td>' +
            '<td headers="status" class="csvReportStatus">' + getStatusIcon(job.status, job.statusDescription) + '</td>' +
            '<td headers="detail" class="csvReportDetail">' + btnToDetail + '</td>' +
          '</tr>';
    };

    var getStatusIcon = function(status, description) {
      var icon = {
        1: "images/csv-status-icons/clock-outline.png",
        2: "images/csv-status-icons/check-icon.png",
        3: "images/csv-status-icons/not-ok-check-icon.png",
        4: "images/csv-status-icons/error-icon-small.png",
        99: "images/csv-status-icons/unknown-error.png"
      };
      return '<img src="' + icon[status] + '" title="' + description + '"/>';
    };

    var convertToListItem = function (name, stringList) {
      return '' +
          '<header>' +
            '<h2>' + name + '</h2>' +
          '</header>' +
          '<div class="bigListPopUp">' +
            _.map(_.split(stringList, ","), function (listItem){
              return '<li>' + listItem + '</li>';
            }).join('') +
          '</div>';
    };

  };
})(this);
