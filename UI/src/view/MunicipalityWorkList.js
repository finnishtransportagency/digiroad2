(function (root) {

    var hrefDir = "#municipality/";

    var municipalityTable = function (municipalitiesName, filter) {
        var municipalityValues =
            _.isEmpty(filter) ? municipalitiesName.municipality : _.filter(municipalitiesName.municipality, function (name) {
                return name.toLowerCase().startsWith(filter.toLowerCase());});

        var tableContentRows = function (municipalitiesName) {
            return _.map(municipalitiesName, function (item) {
                return $('<tr/>').append($('<td/>').append(idLink(item)));
            });
        };
        var idLink = function (municipality) {
            return $('<a class="work-list-item"/>').attr('href', hrefDir + municipality).html(municipality);
        };

        //Todo check if is it really needed
        // if (municipalityValues.length == 1)
        //     $('.button').attr("href", hrefDir + municipalityValues);
        // else
        //     $('.button').removeAttr("href");

        return tableContentRows(municipalityValues);
    };

    var searchbox = $('<div class="municipality-list">' +
        '<div class="filter-box">' +
        '<input type="text" class="location input-sm" placeholder="Osoite tai koordinaatit" id="searchBox"></div>');

    var generateWorkList = function (listP) {
        var title = 'Tietolajien kuntasivu';
        $('#work-list').html('' +
            '<div style="overflow: auto;">' +
            '<div class="page">' +
            '<div class="content-box">' +
            '<header>' + title +
            '<a class="header-link" href="#work-list/municipalityWorkList">Sulje</a>' +
            '</header>' +
            '<div class="work-list">' +
            '</div>' +
            '</div>' +
            '</div>'
        );
        var showApp = function () {
            $('.container').show();
            $('#work-list').hide();
            $('body').removeClass('scrollable').scrollTop(0);
            $(window).off('hashchange', showApp);
        };
        $(window).on('hashchange', showApp);

        listP.then(function (limits) {
            var unknownLimits = _.partial.apply(null, [municipalityTable].concat([limits, ""]))();
            var formAndTable = searchbox.append($('<table id="tableData"/>').append(unknownLimits));
            $('#work-list .work-list').html(formAndTable);

            $('#searchBox').on('keyup', function(event){
                var currentInput = event.currentTarget.value;

                var unknownLimits = _.partial.apply(null, [municipalityTable].concat([limits, currentInput]))();
                $('#tableData tbody').html(unknownLimits);

            });
        });
    };

    var bindEvents = function () {
        eventbus.on('municipality:select', function(listP) {
            $('.container').hide();
            $('#work-list').show();
            $('body').addClass('scrollable');
            generateWorkList(listP);
        });
    };

    root.MunicipalityWorkList =  {
        initialize: function () {
            bindEvents();
        }
    };
})(this);