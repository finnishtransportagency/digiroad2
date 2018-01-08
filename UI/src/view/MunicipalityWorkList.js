(function (root) {
    var hrefDir = "#municipality/";

    var municipalityTable = function (municipalities, filter) {
        var municipalityValues =
            _.isEmpty(filter) ? municipalities : _.filter(municipalities, function (municipality) {
                return municipality.name.toLowerCase().startsWith(filter.toLowerCase());});

        var tableContentRows = function (municipalities) {
            return _.map(municipalities, function (municipality) {
                return $('<tr/>').append($('<td/>').append(idLink(municipality)));
            });
        };
        var idLink = function (municipality) {
            return $('<a class="work-list-item"/>').attr('href', hrefDir + municipality.id).html(municipality.name);
        };

        return $('<table id="tableData"/>').append(tableContentRows(municipalityValues));
    };

    var searchbox = $('<div class="filter-box">' +
        '<input type="text" class="location input-sm" placeholder="Osoite tai koordinaatit" id="searchBox"></div>');

    var generateWorkList = function (listP) {
        var title = 'Tietolajien kuntasivu';
        $('#work-list').html('' +
            '<div style="overflow: auto;">' +
            '<div class="page">' +
            '<div class="content-box">' +
            '<header>' + title +
            '<a class="header-link" href="#" onclick="windows.location">Sulje</a>' +
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

             if (_.contains(userRoles, 'operator') || _.contains(userRoles, 'premium')) {
                 $('#work-list .work-list').html($('<div class="municipality-list">').append(searchbox.append(unknownLimits)));
             } else
                 $('#work-list .work-list').append($('<div class="municipality-list">').append(unknownLimits));

            $('#searchBox').on('keyup', function(event){
                var currentInput = event.currentTarget.value;

                var unknownLimits = _.partial.apply(null, [municipalityTable].concat([limits, currentInput]))();
                $('#tableData tbody').html(unknownLimits);
            });
        });
    };

    var bindExternalEventHandlers = function() {
        eventbus.on('roles:fetched', function(roles) {
            userRoles = roles;
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
            var userRoles;
            bindExternalEventHandlers();
            bindEvents();
        }
    };
})(this);