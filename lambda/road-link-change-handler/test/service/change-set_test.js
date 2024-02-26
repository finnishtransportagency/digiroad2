const assert = require('chai').assert;
const {ChangeSet} = require('../../dist/service/change-set');
const {KgvLink} = require("../../dist/client/kgv-client");
const {ReplaceInfo} = require("../../dist/client/vkm-client");

const testLinkGeom1 = "SRID=3067;LINESTRING ZM(331703.099 6668799.208 44.23 0,331699.994 6668800.316 44.069 3.297,331694.381 6668800.442 43.482 8.911,331686.381 6668799.798 42.825 16.937)";

describe('Change Set', function () {
    it('Change set without changes', function () {
        const emptyChangeSet = new ChangeSet([], []);
        assert.equal(emptyChangeSet.toJson(), '[]');
    });

    it('New link added', function () {
        const newLinkId = "testi:1";
        const newLink = new KgvLink(newLinkId, testLinkGeom1, 123, 3, 149, 16.93706266, 0, 12141);
        const change = new ReplaceInfo(undefined, newLinkId, undefined, undefined, 0, 16.937);
        const changeSet = new ChangeSet([newLink], [change]);
        const expected = [{
            "changeType": "add",
            "old": null,
            "new": [
                {
                    "linkId": newLinkId,
                    "linkLength": 16.937,
                    "geometry": testLinkGeom1,
                    "roadClass": 12141,
                    "adminClass": 3,
                    "municipality": 149,
                    "surfaceType": null,
                    "trafficDirection": 0,
                    "lifeCycleStatus": null
                }
            ],
            "replaceInfo": [
                {
                    "oldLinkId": null,
                    "newLinkId": newLinkId,
                    "oldFromMValue": null,
                    "oldToMValue": null,
                    "newFromMValue": 0,
                    "newToMValue": 16.937,
                    "digitizationChange": false
                }
            ]}];
        assert.equal(changeSet.changeEntries[0].changeType, "add");
        assert.equal(changeSet.toJson(), JSON.stringify(expected));
    });

    it('New link added and partial link add', function () {
        const newLinkId = "testi:1";
        const newLinkId2 = "testi:2";
        const newLink = new KgvLink(newLinkId, testLinkGeom1, 123, 3, 149, 16.93706266, 0, 12141);
        const change = new ReplaceInfo(undefined, newLinkId, undefined, undefined, 0, 16.937);
        const newLink2 = new KgvLink(newLinkId2, testLinkGeom1, 123, 3, 149, 16.93706266, 0, 12141);
        const change2 = new ReplaceInfo(undefined, newLinkId2, undefined, undefined, 0, 10.937);

        const changeSet = new ChangeSet([newLink,newLink2], [change,change2]);
        const expected = [{
            "changeType": "add",
            "old": null,
            "new": [
                {
                    "linkId": newLinkId,
                    "linkLength": 16.937,
                    "geometry": testLinkGeom1,
                    "roadClass": 12141,
                    "adminClass": 3,
                    "municipality": 149,
                    "surfaceType": null,
                    "trafficDirection": 0,
                    "lifeCycleStatus": null
                }
            ],
            "replaceInfo": [
                {
                    "oldLinkId": null,
                    "newLinkId": newLinkId,
                    "oldFromMValue": null,
                    "oldToMValue": null,
                    "newFromMValue": 0,
                    "newToMValue": 16.937,
                    "digitizationChange": false
                }
            ]}];

        assert.equal(changeSet.changeEntries[0].changeType, "add");
        assert.equal(changeSet.toJson(), JSON.stringify(expected));
    });

    it('New link added and partial add in multiple part', function () {
        const newLinkId = "testi:1";
        const newLinkId2 = "testi:2";
        const newLink = new KgvLink(newLinkId, testLinkGeom1, 123, 3, 149, 16.93706266, 0, 12141);
        const change = new ReplaceInfo(undefined, newLinkId, undefined, undefined, 0, 16.937);
        const newLink2 = new KgvLink(newLinkId2, testLinkGeom1, 123, 3, 149, 16.93706266, 0, 12141);
        const change2 = new ReplaceInfo(undefined, newLinkId2, undefined, undefined, 0, 5.937);
        const change3 = new ReplaceInfo(undefined, newLinkId2, undefined, undefined, 5.937, 10.937);
        const changeSet = new ChangeSet([newLink,newLink2], [change,change3,change2]);
        const expected = [{
            "changeType": "add",
            "old": null,
            "new": [
                {
                    "linkId": newLinkId,
                    "linkLength": 16.937,
                    "geometry": testLinkGeom1,
                    "roadClass": 12141,
                    "adminClass": 3,
                    "municipality": 149,
                    "surfaceType": null,
                    "trafficDirection": 0,
                    "lifeCycleStatus": null
                }
            ],
            "replaceInfo": [
                {
                    "oldLinkId": null,
                    "newLinkId": newLinkId,
                    "oldFromMValue": null,
                    "oldToMValue": null,
                    "newFromMValue": 0,
                    "newToMValue": 16.937,
                    "digitizationChange": false
                }
            ]}];
        assert.equal(changeSet.changeEntries[0].changeType, "add");
        assert.equal(changeSet.toJson(), JSON.stringify(expected));
    });

    it('New link added and partial add in multiple part, no continuity', function () {
        const newLinkId = "testi:1";
        const newLinkId2 = "testi:2";
        const newLink = new KgvLink(newLinkId, testLinkGeom1, 123, 3, 149, 16.93706266, 0, 12141);
        const change = new ReplaceInfo(undefined, newLinkId, undefined, undefined, 0, 16.937);
        const newLink2 = new KgvLink(newLinkId2, testLinkGeom1, 123, 3, 149, 16.93706266, 0, 12141);
        const change2 = new ReplaceInfo(undefined, newLinkId2, undefined, undefined, 0, 4.937);
        const change3 = new ReplaceInfo(undefined, newLinkId2, undefined, undefined, 5.937,  16.93706266);
        const changeSet = new ChangeSet([newLink,newLink2], [change,change3,change2]);
        const expected = [{
            "changeType": "add",
            "old": null,
            "new": [
                {
                    "linkId": newLinkId,
                    "linkLength": 16.937,
                    "geometry": testLinkGeom1,
                    "roadClass": 12141,
                    "adminClass": 3,
                    "municipality": 149,
                    "surfaceType": null,
                    "trafficDirection": 0,
                    "lifeCycleStatus": null
                }
            ],
            "replaceInfo": [
                {
                    "oldLinkId": null,
                    "newLinkId": newLinkId,
                    "oldFromMValue": null,
                    "oldToMValue": null,
                    "newFromMValue": 0,
                    "newToMValue": 16.937,
                    "digitizationChange": false
                }
            ]}];
        assert.equal(changeSet.changeEntries[0].changeType, "add");
        assert.equal(changeSet.toJson(), JSON.stringify(expected));
    });
    
    it('Two adds in two parts, the complete replace info is merged and the partial is discarded', function () {
        const newLinkId = "testi:1";
        const newLinkId2 = "testi:2";
        const newLink = new KgvLink(newLinkId, testLinkGeom1, 123, 3, 149, 16.93706266, 0, 12141);
        const change = new ReplaceInfo(undefined, newLinkId, undefined, undefined, 0, 10.937);
        const change5 = new ReplaceInfo(undefined, newLinkId, undefined, undefined, 10.937, 16.937);
        const newLink2 = new KgvLink(newLinkId2, testLinkGeom1, 123, 3, 149, 16.93706266, 0, 12141);
        const change2 = new ReplaceInfo(undefined, newLinkId2, undefined, undefined, 0, 5.937);
        const change3 = new ReplaceInfo(undefined, newLinkId2, undefined, undefined, 5.937, 10.937);
        const changeSet = new ChangeSet([newLink,newLink2], [change,change3,change2,change5]);
        const expected = [{
            "changeType": "add",
            "old": null,
            "new": [
                {
                    "linkId": newLinkId,
                    "linkLength": 16.937,
                    "geometry": testLinkGeom1,
                    "roadClass": 12141,
                    "adminClass": 3,
                    "municipality": 149,
                    "surfaceType": null,
                    "trafficDirection": 0,
                    "lifeCycleStatus": null
                }
            ],
            "replaceInfo": [
                {
                    "oldLinkId": null,
                    "newLinkId": newLinkId,
                    "oldFromMValue": null,
                    "oldToMValue": null,
                    "newFromMValue": 0,
                    "newToMValue": 16.937,
                    "digitizationChange": false
                }
            ]}];
        assert.equal(changeSet.changeEntries[0].changeType, "add");
        assert.equal(changeSet.toJson(), JSON.stringify(expected));
    });

    it('Link removed', function () {
        const linkId = "testi:1";
        const oldLink = new KgvLink(linkId, testLinkGeom1, 123, 3, 149, 16.93706266, 0, 12141);
        const change = new ReplaceInfo(linkId, undefined, 0, 16.937, undefined, undefined);
        const changeSet = new ChangeSet([oldLink], [change]);
        const expected = [{
            "changeType": "remove",
            "old": {
                "linkId": linkId,
                "linkLength": 16.937,
                "geometry": testLinkGeom1,
                "roadClass": 12141,
                "adminClass": 3,
                "municipality": 149,
                "surfaceType": null,
                "trafficDirection": 0,
                "lifeCycleStatus": null
            },
            "new": [],
            "replaceInfo": [
                {
                    "oldLinkId": linkId,
                    "newLinkId": null,
                    "oldFromMValue": 0,
                    "oldToMValue": 16.937,
                    "newFromMValue": null,
                    "newToMValue": null,
                    "digitizationChange": false
                }
            ]}];
        assert.equal(changeSet.changeEntries[0].changeType, "remove");
        assert.equal(changeSet.toJson(), JSON.stringify(expected));
    });

    it('Link replaced', function () {
        const oldLinkId = "link:1";
        const newLinkId = "link:2";
        const oldLink = new KgvLink(oldLinkId, testLinkGeom1, 123, 3, 149, 16.93706266, 0, undefined, 1);
        const newLink = new KgvLink(newLinkId, testLinkGeom1, 124, 3, 149, 16.93786266, 0, 12141, 2);
        const change = new ReplaceInfo(oldLinkId, newLinkId, 0, 16.937, 0, 16.937);
        const changeSet = new ChangeSet([oldLink, newLink], [change]);
        const expected = [{
            "changeType": "replace",
            "old": {
                "linkId": oldLinkId,
                "linkLength": 16.937,
                "geometry": testLinkGeom1,
                "roadClass": null,
                "adminClass": 3,
                "municipality": 149,
                "surfaceType": 1,
                "trafficDirection": 0,
                "lifeCycleStatus": null
            },
            "new": [
                {
                    "linkId": newLinkId,
                    "linkLength": 16.938,
                    "geometry": testLinkGeom1,
                    "roadClass": 12141,
                    "adminClass": 3,
                    "municipality": 149,
                    "surfaceType": 2,
                    "trafficDirection": 0,
                    "lifeCycleStatus": null
                }
            ],
            "replaceInfo": [
                {
                    "oldLinkId": oldLinkId,
                    "newLinkId": newLinkId,
                    "oldFromMValue": 0,
                    "oldToMValue": 16.937,
                    "newFromMValue": 0,
                    "newToMValue": 16.937,
                    "digitizationChange": false
                }
            ]}];
        assert.equal(changeSet.changeEntries[0].changeType, "replace");
        assert.equal(changeSet.toJson(), JSON.stringify(expected));
    });

    it('Link split to multiple new links', function () {
        const oldGeom  = 'SRID=3067;LINESTRING ZM(463081.329 6750374.612 78.202 0,463082.714 6750385.729 78.269 11.203,463084.009 6750395.805 78.593 21.362,463086.129 6750410.791 79.371 36.497,463087.523 6750420.157 79.826 45.966,463086.688 6750429.784 80.3 55.629,463084.137 6750441.187 80.807 67.314,463080.461 6750451.094 81.275 77.881,463075.128 6750461.473 81.803 89.55,463070.188 6750469.693 82.19 99.14,463065.041 6750476.849 82.491 107.955,463056.939 6750486.33 82.464 120.426)';
        const newGeom1 = 'SRID=3067;LINESTRING ZM(463081.329 6750374.612 78.202 0,463081.819 6750378.557 78.147 3.975)';
        const newGeom2 = 'SRID=3067;LINESTRING ZM(463081.819 6750378.557 78.147 0,463082.714 6750385.729 78.269 7.228,463084.009 6750395.805 78.593 17.387,463086.129 6750410.791 79.371 32.522,463087.523 6750420.157 79.826 41.991,463086.688 6750429.784 80.3 51.654,463084.137 6750441.187 80.807 63.339,463080.461 6750451.094 81.275 73.906,463075.128 6750461.473 81.803 85.575,463070.188 6750469.693 82.19 95.165,463065.041 6750476.849 82.491 103.98,463060.195 6750482.577 82.567 111.483)';
        const newGeom3 = 'SRID=3067;LINESTRING ZM(463060.195 6750482.577 82.567 0,463056.939 6750486.33 82.464 4.969)';

        const oldLink  = new KgvLink("old:1", oldGeom, 123, 2, 142, 120.42636809, 0, 12131);
        const newLink1 = new KgvLink("new1:1", newGeom1, 124, 2, 142, 3.97531445, 0, 12131);
        const newLink2 = new KgvLink("new2:2", newGeom2, 125, 2, 142, 111.48272932, 0, 12131);
        const newLink3 = new KgvLink("new3:3", newGeom3, 126, 2, 142, 4.96855563, 0, 12131);

        const changes = [
            new ReplaceInfo(oldLink.id, newLink1.id, 0, 3.975, 0, 3.975),
            new ReplaceInfo(oldLink.id, newLink2.id, 3.975, 115.458, 0, 111.483),
            new ReplaceInfo(oldLink.id, newLink3.id, 120.426, 115.458, 0, 4.968)
        ];

        const changeSet = new ChangeSet([oldLink, newLink1, newLink2, newLink3], changes);
        const expected = [{
            "changeType": "split",
            "old": {
                "linkId": oldLink.id,
                "linkLength": oldLink.length,
                "geometry": oldLink.geometry,
                "roadClass": oldLink.roadClass,
                "adminClass": oldLink.adminClass,
                "municipality": oldLink.municipality,
                "surfaceType": null,
                "trafficDirection": oldLink.directionType,
                "lifeCycleStatus": null
            },
            "new": [
                {
                    "linkId": newLink1.id,
                    "linkLength": newLink1.length,
                    "geometry": newLink1.geometry,
                    "roadClass": newLink1.roadClass,
                    "adminClass": newLink1.adminClass,
                    "municipality": newLink1.municipality,
                    "surfaceType": null,
                    "trafficDirection": newLink1.directionType,
                    "lifeCycleStatus": null
                },
                {
                    "linkId": newLink2.id,
                    "linkLength": newLink2.length,
                    "geometry": newLink2.geometry,
                    "roadClass": newLink2.roadClass,
                    "adminClass": newLink2.adminClass,
                    "municipality": newLink2.municipality,
                    "surfaceType": null,
                    "trafficDirection": newLink2.directionType,
                    "lifeCycleStatus": null,
                },
                {
                    "linkId": newLink3.id,
                    "linkLength": newLink3.length,
                    "geometry": newLink3.geometry,
                    "roadClass": newLink3.roadClass,
                    "adminClass": newLink3.adminClass,
                    "municipality": newLink3.municipality,
                    "surfaceType": null,
                    "trafficDirection": newLink3.directionType,
                    "lifeCycleStatus": null,
                }
            ],
            "replaceInfo": [
                {
                    "oldLinkId": oldLink.id,
                    "newLinkId": newLink1.id,
                    "oldFromMValue": 0,
                    "oldToMValue": 3.975,
                    "newFromMValue": 0,
                    "newToMValue": 3.975,
                    "digitizationChange": false
                },
                {
                    "oldLinkId": oldLink.id,
                    "newLinkId": newLink2.id,
                    "oldFromMValue": 3.975,
                    "oldToMValue": 115.458,
                    "newFromMValue": 0,
                    "newToMValue": 111.483,
                    "digitizationChange": false
                },
                {
                    "oldLinkId": oldLink.id,
                    "newLinkId": newLink3.id,
                    "oldFromMValue": 120.426,
                    "oldToMValue": 115.458,
                    "newFromMValue": 0,
                    "newToMValue": 4.968,
                    "digitizationChange": true
                }
            ]
        }];
        assert.equal(changeSet.changeEntries[0].changeType, "split");
        assert.equal(changeSet.toJson(), JSON.stringify(expected));
    });

    it('Link split, one part is removed and other moved', function () {
        const oldGeom  = 'SRID=3067;LINESTRING ZM(463081.329 6750374.612 78.202 0,463082.714 6750385.729 78.269 11.203,463084.009 6750395.805 78.593 21.362,463086.129 6750410.791 79.371 36.497,463087.523 6750420.157 79.826 45.966,463086.688 6750429.784 80.3 55.629,463084.137 6750441.187 80.807 67.314,463080.461 6750451.094 81.275 77.881,463075.128 6750461.473 81.803 89.55,463070.188 6750469.693 82.19 99.14,463065.041 6750476.849 82.491 107.955,463056.939 6750486.33 82.464 120.426)';
        const newGeom1 = 'SRID=3067;LINESTRING ZM(463081.819 6750378.557 78.147 0,463082.714 6750385.729 78.269 7.228,463084.009 6750395.805 78.593 17.387,463086.129 6750410.791 79.371 32.522,463087.523 6750420.157 79.826 41.991,463086.688 6750429.784 80.3 51.654,463084.137 6750441.187 80.807 63.339,463080.461 6750451.094 81.275 73.906,463075.128 6750461.473 81.803 85.575,463070.188 6750469.693 82.19 95.165,463065.041 6750476.849 82.491 103.98,463060.195 6750482.577 82.567 111.483)';

        const oldLink  = new KgvLink("old:1", oldGeom, 123, 2, 142, 120.42636809, 0, 12131);
        const newLink1 = new KgvLink("new2:1", newGeom1, 125, 2, 142, 111.48272932, 0, 12131);

        const changes = [
            new ReplaceInfo(oldLink.id, null, 0, 3.975, null, null),
            new ReplaceInfo(oldLink.id, newLink1.id, 3.975, 115.458, 0, 111.483),
        ];

        const changeSet = new ChangeSet([oldLink, newLink1], changes);
        const expected = [{
            "changeType": "split",
            "old": {
                "linkId": oldLink.id,
                "linkLength": oldLink.length,
                "geometry": oldLink.geometry,
                "roadClass": oldLink.roadClass,
                "adminClass": oldLink.adminClass,
                "municipality": oldLink.municipality,
                "surfaceType": null,
                "trafficDirection": oldLink.directionType,
                "lifeCycleStatus": null,
            },
            "new": [
                {
                    "linkId": newLink1.id,
                    "linkLength": newLink1.length,
                    "geometry": newLink1.geometry,
                    "roadClass": newLink1.roadClass,
                    "adminClass": newLink1.adminClass,
                    "municipality": newLink1.municipality,
                    "surfaceType": null,
                    "trafficDirection": newLink1.directionType,
                    "lifeCycleStatus": null,
                },
            ],
            "replaceInfo": [
                {
                    "oldLinkId": oldLink.id,
                    "newLinkId": null,
                    "oldFromMValue": 0,
                    "oldToMValue": 3.975,
                    "newFromMValue": null,
                    "newToMValue": null,
                    "digitizationChange": false
                },
                {
                    "oldLinkId": oldLink.id,
                    "newLinkId": newLink1.id,
                    "oldFromMValue": 3.975,
                    "oldToMValue": 115.458,
                    "newFromMValue": 0,
                    "newToMValue": 111.483,
                    "digitizationChange": false
                },
            ]
        }];
        assert.equal(changeSet.changeEntries[0].changeType, "split");
        assert.equal(changeSet.toJson(), JSON.stringify(expected));
    });

    it('Links merged', function () {
        const oldGeom1 = 'SRID=3067;LINESTRING ZM(426632.935 7111261.572 96.606 0,426634.266 7111272.439 96.676 10.948,426637.384 7111283.768 96.976 22.698,426641.674 7111296.6 97.275 36.229,426648.54 7111310.958 97.832 52.144,426654.383 7111321.984 98.023 64.622,426654.483 7111322.164 98.023 64.828)';
        const oldGeom2 = 'SRID=3067;LINESTRING ZM(426654.483 7111322.164 98.023 0,426658.886 7111330.74 98.044 9.64,426661.739 7111340.464 97.981 19.774,426664.116 7111346.486 97.987 26.248,426668.526 7111350.387 97.956 32.136)';
        const newGeom  = 'SRID=3067;LINESTRING ZM(426632.935 7111261.572 96.606 0,426634.266 7111272.439 96.676 10.948,426637.384 7111283.768 96.976 22.698,426641.674 7111296.6 97.275 36.229,426648.54 7111310.958 97.832 52.144,426654.383 7111321.984 98.023 64.622,426654.483 7111322.164 98.023 64.828,426658.886 7111330.74 98.044 74.468,426661.739 7111340.464 97.981 84.602,426664.116 7111346.486 97.987 91.076,426668.526 7111350.387 97.956 96.964)';

        const oldLink1 = new KgvLink("old1:1", oldGeom1, 123, 3, 71, 64.82821897, 0, 12141);
        const oldLink2 = new KgvLink("old2:1", oldGeom2, 124, 3, 71, 32.13605585, 0, 12141);
        const newLink  = new KgvLink("new:1", newGeom, 125, 3, 71, 96.96427481, 0, 12141);

        const changes = [
            new ReplaceInfo(oldLink1.id, newLink.id, 0, 64.828, 0, 64.82796475216877),
            new ReplaceInfo(oldLink2.id, newLink.id, 0, 32.136, 64.828, 96.964),
        ];

        const changeSet = new ChangeSet([oldLink1, oldLink2, newLink], changes);
        const expected = [
            {
                "changeType": "replace",
                "old": {
                    "linkId": oldLink1.id,
                    "linkLength": oldLink1.length,
                    "geometry": oldLink1.geometry,
                    "roadClass": oldLink1.roadClass,
                    "adminClass": oldLink1.adminClass,
                    "municipality": oldLink1.municipality,
                    "surfaceType": null,
                    "trafficDirection": oldLink1.directionType,
                    "lifeCycleStatus": null
                },
                "new": [
                    {
                        "linkId": newLink.id,
                        "linkLength": newLink.length,
                        "geometry": newLink.geometry,
                        "roadClass": newLink.roadClass,
                        "adminClass": newLink.adminClass,
                        "municipality": newLink.municipality,
                        "surfaceType": null,
                        "trafficDirection": newLink.directionType,
                        "lifeCycleStatus": null
                    }
                ],
                "replaceInfo": [
                    {
                        "oldLinkId": oldLink1.id,
                        "newLinkId": newLink.id,
                        "oldFromMValue": 0,
                        "oldToMValue": 64.828,
                        "newFromMValue": 0,
                        "newToMValue": 64.82796475216877,
                        "digitizationChange": false
                    }
                ]
            },
            {
                "changeType": "replace",
                "old": {
                    "linkId": oldLink2.id,
                    "linkLength": oldLink2.length,
                    "geometry": oldLink2.geometry,
                    "roadClass": oldLink2.roadClass,
                    "adminClass": oldLink2.adminClass,
                    "municipality": oldLink2.municipality,
                    "surfaceType": null,
                    "trafficDirection": oldLink2.directionType,
                    "lifeCycleStatus": null
                },
                "new": [
                    {
                        "linkId": newLink.id,
                        "linkLength": newLink.length,
                        "geometry": newLink.geometry,
                        "roadClass": newLink.roadClass,
                        "adminClass": newLink.adminClass,
                        "municipality": newLink.municipality,
                        "surfaceType": null,
                        "trafficDirection": newLink.directionType,
                        "lifeCycleStatus": null
                    }
                ],
                "replaceInfo": [
                    {
                        "oldLinkId": oldLink2.id,
                        "newLinkId": newLink.id,
                        "oldFromMValue": 0,
                        "oldToMValue": 32.136,
                        "newFromMValue": 64.828,
                        "newToMValue": 96.964,
                        "digitizationChange": false
                    }
                ]
            }
        ];
        assert.equal(changeSet.changeEntries[0].changeType, "replace");
        assert.equal(changeSet.changeEntries[1].changeType, "replace");
        assert.equal(changeSet.toJson(), JSON.stringify(expected));
    });

    it("Merge replace info with same old and new link and duplicate info", () => {
        const oldGeom = "SRID=3067;LINESTRING ZM(302629.23 6736671.775 99.544 0, 302621.967 6736683.534 99.589 13.821, 302611.024 6736702.979 99.581 36.134, 302602.994 6736717.236 99.534 52.497, 302594.308 6736732.232 99.561 69.827, 302586.357 6736746.144 99.552 85.85, 302581.219 6736754.425 99.667 95.596, 302577.134 6736760.858 99.676 103.216, 302572.192 6736769.964 99.677 113.577, 302562.001 6736788.489 99.568 134.72, 302554.351 6736802.596 99.491 150.768, 302549.547 6736810.844 99.491 160.313, 302544.276 6736819.477 99.472 170.428, 302538.66 6736827.468 99.473 180.195, 302533.188 6736834.659 99.482 189.231)"
        const newGeom = "SRID=3067;LINESTRING ZM(302630.423 6736669.762 99.529 0, 302621.967 6736683.534 99.589 16.161, 302611.024 6736702.979 99.581 38.474, 302602.994 6736717.236 99.534 54.836, 302594.308 6736732.232 99.561 72.166, 302581.219 6736754.425 99.667 97.932, 302572.192 6736769.964 99.677 115.902, 302562.001 6736788.489 99.568 137.045, 302554.351 6736802.596 99.491 153.093, 302544.276 6736819.477 99.472 172.752, 302533.188 6736834.659 99.482 191.552)"

        const oldLink = new KgvLink("oldId:1", oldGeom, 123, 1, 761, 189.231, 0, 12131, 1)
        const newLink = new KgvLink("newId:1", newGeom, 124, 1, 761, 191.552, 0, 12131, 1)

        const changes = [
            new ReplaceInfo(oldLink.id, newLink.id, 0, 132.864, 0, 134.494),
            new ReplaceInfo(oldLink.id, newLink.id, 0, 132.864, 0, 134.494),
            new ReplaceInfo(oldLink.id, newLink.id, 132.864, 189.231, 134.494, 191.552)
        ];

        const changeSet = new ChangeSet([oldLink, newLink], changes)
        const expected = [
            {
                "changeType": "replace",
                "old": {
                    "linkId": "oldId:1",
                    "linkLength": 189.231,
                    "geometry": "SRID=3067;LINESTRING ZM(302629.23 6736671.775 99.544 0, 302621.967 6736683.534 99.589 13.821, 302611.024 6736702.979 99.581 36.134, 302602.994 6736717.236 99.534 52.497, 302594.308 6736732.232 99.561 69.827, 302586.357 6736746.144 99.552 85.85, 302581.219 6736754.425 99.667 95.596, 302577.134 6736760.858 99.676 103.216, 302572.192 6736769.964 99.677 113.577, 302562.001 6736788.489 99.568 134.72, 302554.351 6736802.596 99.491 150.768, 302549.547 6736810.844 99.491 160.313, 302544.276 6736819.477 99.472 170.428, 302538.66 6736827.468 99.473 180.195, 302533.188 6736834.659 99.482 189.231)",
                    "roadClass": 12131,
                    "adminClass": 1,
                    "municipality": 761,
                    "surfaceType": 1,
                    "trafficDirection": 0,
                    "lifeCycleStatus": null
                },
                "new": [
                    {
                        "linkId": "newId:1",
                        "linkLength": 191.552,
                        "geometry": "SRID=3067;LINESTRING ZM(302630.423 6736669.762 99.529 0, 302621.967 6736683.534 99.589 16.161, 302611.024 6736702.979 99.581 38.474, 302602.994 6736717.236 99.534 54.836, 302594.308 6736732.232 99.561 72.166, 302581.219 6736754.425 99.667 97.932, 302572.192 6736769.964 99.677 115.902, 302562.001 6736788.489 99.568 137.045, 302554.351 6736802.596 99.491 153.093, 302544.276 6736819.477 99.472 172.752, 302533.188 6736834.659 99.482 191.552)",
                        "roadClass": 12131,
                        "adminClass": 1,
                        "municipality": 761,
                        "surfaceType": 1,
                        "trafficDirection": 0,
                        "lifeCycleStatus": null
                    }
                ],
                "replaceInfo": [
                    {
                        "oldLinkId": "oldId:1",
                        "newLinkId": "newId:1",
                        "oldFromMValue": 0,
                        "oldToMValue": 189.231,
                        "newFromMValue": 0,
                        "newToMValue": 191.552,
                        "digitizationChange": false
                    }
                ]
            }
        ]
        assert.equal(changeSet.changeEntries[0].changeType, "replace");
        assert.equal(changeSet.toJson(), JSON.stringify(expected));
    })

    it("Replace info with same new and old link with a gap in between should not be merged", () => {
        const oldGeom = "SRID=3067;LINESTRING ZM(336731.572 6668238.505 47.12 0, 336731.071 6668237.971 47.114 0.732, 336719.805 6668252.37 47.034 19.015, 336706.343 6668262.338 46.217 35.766, 336685.644 6668263.461 44.76 56.495, 336660.601 6668266.357 44.996 81.705, 336637.817 6668272.733 45.721 105.364, 336617.073 6668284.707 45.4 129.316, 336593.12 6668293.242 45.067 154.744, 336575.116 6668305.306 44.919 176.416, 336565.972 6668327.195 44.944 200.139, 336558.429 6668344.799 45.321 219.291, 336546.583 6668360.382 45.064 238.865, 336535.508 6668379.091 44.777 260.606, 336531.173 6668395.396 44.239 277.478, 336516.521 6668409.418 44.015 297.758, 336501.304 6668427.361 44.317 321.285, 336479.68 6668438.531 43.651 345.623, 336456.198 6668445.782 43.684 370.2, 336443.887 6668447.599 44.128 382.644)"
        const newGeom = "SRID=3067;LINESTRING ZM(336730.732 6668231.852 47.069 0, 336726.016 6668234.066 47.065 5.21, 336721.175 6668241.045 47.182 13.703, 336718.115 6668250.984 46.97 24.103, 336713.661 6668257.785 46.771 32.233, 336707.767 6668262.767 46.172 39.95, 336695.91 6668263.737 45.722 51.847, 336684.83 6668262.541 44.839 62.991, 336674.391 6668263.619 44.814 73.486, 336660.601 6668266.357 44.996 87.545, 336648.06 6668269.517 45.287 100.478, 336634.801 6668275.004 45.618 114.827, 336617.073 6668284.707 45.4 135.037, 336603.932 6668289.945 45.657 149.183, 336596.167 6668287.62 45.698 157.289, 336589.513 6668282.457 47.321 165.711, 336579.806 6668279.131 48.246 175.972, 336566.282 6668283.611 48.206 190.219, 336549.697 6668287.095 48.403 207.166, 336542.165 6668293.382 49.704 216.977, 336542.1930000001 6668302.409 48.566 226.004, 336546.028 6668312.168 46.559 236.489, 336551.913 6668321.622 44.884 247.625, 336558.469 6668331.149 45.064 259.19, 336559.578 6668339.574 45.578 267.688, 336554.91 6668349.51 45.196 278.666, 336546.583 6668360.382 45.064 292.36, 336538.831 6668371.64 45.18 306.029, 336535.913 6668380.637 44.592 315.487, 336536.353 6668389.81 44.133 324.671, 336535.028 6668395.595 44.004 330.606, 336526.247 6668401.813 43.938 341.365, 336516.504 6668410.646 43.995 354.516, 336507.251 6668421.268 44.209 368.603, 336496.653 6668431.073 44.165 383.041, 336486.974 6668436.51 43.81 394.143, 336470.661 6668443.611 43.437 411.934, 336459.407 6668446.369 43.502 423.522, 336447.212 6668449.416 44.02 436.091)"

        const oldLink = new KgvLink("oldId:1", oldGeom, 123, null, 149, 382.644, 0, 12316, 1)
        const newLink = new KgvLink("newId:1", newGeom, 124, null, 149, 436.091, 0, 12316, 1)

        const changes = [
            new ReplaceInfo(oldLink.id, newLink.id, 0, 149.987, 0, 157.289),
            new ReplaceInfo(oldLink.id, newLink.id, 206.728, 382.644, 259.19, 436.091),
            new ReplaceInfo(oldLink.id, null, 149.987, 206.728, null, null)
        ];

        const changeSet = new ChangeSet([oldLink, newLink], changes)
        const expected = [
            {
                "changeType": "split",
                "old": {
                    "linkId": "oldId:1",
                    "linkLength": 382.644,
                    "geometry": "SRID=3067;LINESTRING ZM(336731.572 6668238.505 47.12 0, 336731.071 6668237.971 47.114 0.732, 336719.805 6668252.37 47.034 19.015, 336706.343 6668262.338 46.217 35.766, 336685.644 6668263.461 44.76 56.495, 336660.601 6668266.357 44.996 81.705, 336637.817 6668272.733 45.721 105.364, 336617.073 6668284.707 45.4 129.316, 336593.12 6668293.242 45.067 154.744, 336575.116 6668305.306 44.919 176.416, 336565.972 6668327.195 44.944 200.139, 336558.429 6668344.799 45.321 219.291, 336546.583 6668360.382 45.064 238.865, 336535.508 6668379.091 44.777 260.606, 336531.173 6668395.396 44.239 277.478, 336516.521 6668409.418 44.015 297.758, 336501.304 6668427.361 44.317 321.285, 336479.68 6668438.531 43.651 345.623, 336456.198 6668445.782 43.684 370.2, 336443.887 6668447.599 44.128 382.644)",
                    "roadClass": 12316,
                    "adminClass": null,
                    "municipality": 149,
                    "surfaceType": 1,
                    "trafficDirection": 0,
                    "lifeCycleStatus": null
                },
                "new": [
                    {
                        "linkId": "newId:1",
                        "linkLength": 436.091,
                        "geometry": "SRID=3067;LINESTRING ZM(336730.732 6668231.852 47.069 0, 336726.016 6668234.066 47.065 5.21, 336721.175 6668241.045 47.182 13.703, 336718.115 6668250.984 46.97 24.103, 336713.661 6668257.785 46.771 32.233, 336707.767 6668262.767 46.172 39.95, 336695.91 6668263.737 45.722 51.847, 336684.83 6668262.541 44.839 62.991, 336674.391 6668263.619 44.814 73.486, 336660.601 6668266.357 44.996 87.545, 336648.06 6668269.517 45.287 100.478, 336634.801 6668275.004 45.618 114.827, 336617.073 6668284.707 45.4 135.037, 336603.932 6668289.945 45.657 149.183, 336596.167 6668287.62 45.698 157.289, 336589.513 6668282.457 47.321 165.711, 336579.806 6668279.131 48.246 175.972, 336566.282 6668283.611 48.206 190.219, 336549.697 6668287.095 48.403 207.166, 336542.165 6668293.382 49.704 216.977, 336542.1930000001 6668302.409 48.566 226.004, 336546.028 6668312.168 46.559 236.489, 336551.913 6668321.622 44.884 247.625, 336558.469 6668331.149 45.064 259.19, 336559.578 6668339.574 45.578 267.688, 336554.91 6668349.51 45.196 278.666, 336546.583 6668360.382 45.064 292.36, 336538.831 6668371.64 45.18 306.029, 336535.913 6668380.637 44.592 315.487, 336536.353 6668389.81 44.133 324.671, 336535.028 6668395.595 44.004 330.606, 336526.247 6668401.813 43.938 341.365, 336516.504 6668410.646 43.995 354.516, 336507.251 6668421.268 44.209 368.603, 336496.653 6668431.073 44.165 383.041, 336486.974 6668436.51 43.81 394.143, 336470.661 6668443.611 43.437 411.934, 336459.407 6668446.369 43.502 423.522, 336447.212 6668449.416 44.02 436.091)",
                        "roadClass": 12316,
                        "adminClass": null,
                        "municipality": 149,
                        "surfaceType": 1,
                        "trafficDirection": 0,
                        "lifeCycleStatus": null
                    }
                ],
                "replaceInfo": [
                    {
                        "oldLinkId": "oldId:1",
                        "newLinkId": "newId:1",
                        "oldFromMValue": 0,
                        "oldToMValue": 149.987,
                        "newFromMValue": 0,
                        "newToMValue": 157.289,
                        "digitizationChange": false
                    },
                    {
                        "oldLinkId": "oldId:1",
                        "newLinkId": "newId:1",
                        "oldFromMValue": 206.728,
                        "oldToMValue": 382.644,
                        "newFromMValue": 259.19,
                        "newToMValue": 436.091,
                        "digitizationChange": false
                    },
                    {
                        "oldLinkId": "oldId:1",
                        "newLinkId": null,
                        "oldFromMValue": 149.987,
                        "oldToMValue": 206.728,
                        "newFromMValue": null,
                        "newToMValue": null,
                        "digitizationChange": false
                    }
                ]
            }
        ]
        assert.equal(changeSet.changeEntries[0].changeType, "split");
        assert.equal(changeSet.toJson(), JSON.stringify(expected));
    })

    it("Merge replace info with a continuous part and a removed part", () => {
        const oldGeom = "SRID=3067;LINESTRING ZM(307536.178 6732995.741 107.138 0, 307553.37 6732999.216 107.648 17.54, 307569.8400000001 6733005.565 107.903 35.191, 307573.991 6733016.455 108.394 46.845, 307578.371 6733031.993 108.556 62.989, 307584.776 6733051.283 110.298 83.314, 307594.7040000001 6733071.203 110.203 105.571, 307608.519 6733087.737 109.344 127.117)"
        const newGeom = "SRID=3067;LINESTRING ZM(307536.178 6732995.741 107.138 0, 307544.526 6732996.661 107.453 8.399, 307555.85 6732999.489 107.733 20.07, 307562.906 6733001.583 107.827 27.43, 307570.934 6733007.389 108.021 37.338, 307573.442 6733016.963 108.419 47.235, 307574.045 6733028.143 108.646 58.431, 307574.645 6733035.667 108.834 65.979, 307577.133 6733044.197 109.733 74.865, 307580.857 6733051.135 110.52 82.739, 307587.554 6733061.221 110.59 94.846, 307597.241 6733075.199 110.238 111.852)"

        const oldLink = new KgvLink("oldId:1", oldGeom, 123, 3, 761, 127.117, 0, 12141, 1)
        const newLink = new KgvLink("newId:1", newGeom, 124, 3, 761, 111.852, 0, 12141, 1)

        const changes = [
            new ReplaceInfo(oldLink.id, null, 110.265, 127.117, null, null),
            new ReplaceInfo(oldLink.id, newLink.id, 0, 90.349, 0, 90.349),
            new ReplaceInfo(oldLink.id, newLink.id, 90.349, 110.265, 90.349, 110.265)
        ];

        const changeSet = new ChangeSet([oldLink, newLink], changes)
        const expected = [
            {
                "changeType": "split",
                "old": {
                    "linkId": "oldId:1",
                    "linkLength": 127.117,
                    "geometry": "SRID=3067;LINESTRING ZM(307536.178 6732995.741 107.138 0, 307553.37 6732999.216 107.648 17.54, 307569.8400000001 6733005.565 107.903 35.191, 307573.991 6733016.455 108.394 46.845, 307578.371 6733031.993 108.556 62.989, 307584.776 6733051.283 110.298 83.314, 307594.7040000001 6733071.203 110.203 105.571, 307608.519 6733087.737 109.344 127.117)",
                    "roadClass": 12141,
                    "adminClass": 3,
                    "municipality": 761,
                    "surfaceType": 1,
                    "trafficDirection": 0,
                    "lifeCycleStatus": null
                },
                "new": [
                    {
                        "linkId": "newId:1",
                        "linkLength": 111.852,
                        "geometry": "SRID=3067;LINESTRING ZM(307536.178 6732995.741 107.138 0, 307544.526 6732996.661 107.453 8.399, 307555.85 6732999.489 107.733 20.07, 307562.906 6733001.583 107.827 27.43, 307570.934 6733007.389 108.021 37.338, 307573.442 6733016.963 108.419 47.235, 307574.045 6733028.143 108.646 58.431, 307574.645 6733035.667 108.834 65.979, 307577.133 6733044.197 109.733 74.865, 307580.857 6733051.135 110.52 82.739, 307587.554 6733061.221 110.59 94.846, 307597.241 6733075.199 110.238 111.852)",
                        "roadClass": 12141,
                        "adminClass": 3,
                        "municipality": 761,
                        "surfaceType": 1,
                        "trafficDirection": 0,
                        "lifeCycleStatus": null
                    }
                ],
                "replaceInfo": [
                    {
                        "oldLinkId": "oldId:1",
                        "newLinkId": null,
                        "oldFromMValue": 110.265,
                        "oldToMValue": 127.117,
                        "newFromMValue": null,
                        "newToMValue": null,
                        "digitizationChange": false
                    },
                    {
                        "oldLinkId": "oldId:1",
                        "newLinkId": "newId:1",
                        "oldFromMValue": 0,
                        "oldToMValue": 110.265,
                        "newFromMValue": 0,
                        "newToMValue": 110.265,
                        "digitizationChange": false
                    }
                ]
            }
        ]
        assert.equal(changeSet.changeEntries[0].changeType, "split");
        assert.equal(changeSet.toJson(), JSON.stringify(expected));
    })

    it("Merge replace info with several gaps and continuous parts", () => {
        const oldGeom = "SRID=3067;LINESTRING ZM(307536.178 6732995.741 107.138 0, 307553.37 6732999.216 107.648 17.54, 307569.8400000001 6733005.565 107.903 35.191, 307573.991 6733016.455 108.394 46.845, 307578.371 6733031.993 108.556 62.989, 307584.776 6733051.283 110.298 83.314, 307594.7040000001 6733071.203 110.203 105.571, 307608.519 6733087.737 109.344 127.117)"
        const newGeom = "SRID=3067;LINESTRING ZM(307536.178 6732995.741 107.138 0, 307544.526 6732996.661 107.453 8.399, 307555.85 6732999.489 107.733 20.07, 307562.906 6733001.583 107.827 27.43, 307570.934 6733007.389 108.021 37.338, 307573.442 6733016.963 108.419 47.235, 307574.045 6733028.143 108.646 58.431, 307574.645 6733035.667 108.834 65.979, 307577.133 6733044.197 109.733 74.865, 307580.857 6733051.135 110.52 82.739, 307587.554 6733061.221 110.59 94.846, 307597.241 6733075.199 110.238 111.852)"

        const oldLink = new KgvLink("oldId:1", oldGeom, 123, 3, 761, 127.117, 0, 12141, 1)
        const newLink = new KgvLink("newId:1", newGeom, 124, 3, 761, 111.852, 0, 12141, 1)

        const changes = [
            new ReplaceInfo(oldLink.id, newLink.id, 0, 5.349, 0, 5.349),
            new ReplaceInfo(oldLink.id, null, 110.265, 127.117, null, null),
            new ReplaceInfo(oldLink.id, newLink.id, 5.349, 10.349, 5.349, 10.349),
            new ReplaceInfo(oldLink.id, null, 10.349, 20, null, null),
            new ReplaceInfo(oldLink.id, newLink.id, 20, 50, 20, 50),
            new ReplaceInfo(oldLink.id, newLink.id, 50, 90.349, 50, 90.349),
            new ReplaceInfo(oldLink.id, newLink.id, 90.349, 110.265, 90.349, 110.265)
        ];

        const changeSet = new ChangeSet([oldLink, newLink], changes)
        const expected = [
            {
                "changeType": "split",
                "old": {
                    "linkId": "oldId:1",
                    "linkLength": 127.117,
                    "geometry": "SRID=3067;LINESTRING ZM(307536.178 6732995.741 107.138 0, 307553.37 6732999.216 107.648 17.54, 307569.8400000001 6733005.565 107.903 35.191, 307573.991 6733016.455 108.394 46.845, 307578.371 6733031.993 108.556 62.989, 307584.776 6733051.283 110.298 83.314, 307594.7040000001 6733071.203 110.203 105.571, 307608.519 6733087.737 109.344 127.117)",
                    "roadClass": 12141,
                    "adminClass": 3,
                    "municipality": 761,
                    "surfaceType": 1,
                    "trafficDirection": 0,
                    "lifeCycleStatus": null
                },
                "new": [
                    {
                        "linkId": "newId:1",
                        "linkLength": 111.852,
                        "geometry": "SRID=3067;LINESTRING ZM(307536.178 6732995.741 107.138 0, 307544.526 6732996.661 107.453 8.399, 307555.85 6732999.489 107.733 20.07, 307562.906 6733001.583 107.827 27.43, 307570.934 6733007.389 108.021 37.338, 307573.442 6733016.963 108.419 47.235, 307574.045 6733028.143 108.646 58.431, 307574.645 6733035.667 108.834 65.979, 307577.133 6733044.197 109.733 74.865, 307580.857 6733051.135 110.52 82.739, 307587.554 6733061.221 110.59 94.846, 307597.241 6733075.199 110.238 111.852)",
                        "roadClass": 12141,
                        "adminClass": 3,
                        "municipality": 761,
                        "surfaceType": 1,
                        "trafficDirection": 0,
                        "lifeCycleStatus": null
                    }
                ],
                "replaceInfo": [
                    {
                        "oldLinkId": "oldId:1",
                        "newLinkId": "newId:1",
                        "oldFromMValue": 0,
                        "oldToMValue": 10.349,
                        "newFromMValue": 0,
                        "newToMValue": 10.349,
                        "digitizationChange": false
                    },
                    {
                        "oldLinkId": "oldId:1",
                        "newLinkId": "newId:1",
                        "oldFromMValue": 20,
                        "oldToMValue": 110.265,
                        "newFromMValue": 20,
                        "newToMValue": 110.265,
                        "digitizationChange": false
                    },
                    {
                        "oldLinkId": "oldId:1",
                        "newLinkId": null,
                        "oldFromMValue": 10.349,
                        "oldToMValue": 20,
                        "newFromMValue": null,
                        "newToMValue": null,
                        "digitizationChange": false
                    },
                    {
                        "oldLinkId": "oldId:1",
                        "newLinkId": null,
                        "oldFromMValue": 110.265,
                        "oldToMValue": 127.117,
                        "newFromMValue": null,
                        "newToMValue": null,
                        "digitizationChange": false
                    }
                ]
            }
        ]
        assert.equal(changeSet.changeEntries[0].changeType, "split");
        assert.equal(changeSet.toJson(), JSON.stringify(expected));
    })

    it("Merge replace info with add with continuous values", () => {
        const newGeom = "SRID=3067;LINESTRING ZM(245851.419 6803738.024 35.26 0, 245855.406 6803745.129 34.998 8.147, 245863.125 6803761.792 34.724 26.511, 245874.188 6803783.432 34.857 50.815, 245882.652 6803797.428 34.929 67.171, 245888.252 6803808.127 35.085 79.247, 245893.545 6803821.384 35.131 93.522, 245902.181 6803841.019 35.262 114.972, 245908.998 6803856.221 35.212 131.633, 245920.88 6803878.155 35.283 156.578, 245932.734 6803898.913 35.322 180.483, 245941.213 6803913.429 35.398 197.293, 245952.533 6803933.417 35.579 220.264, 245959.582 6803946.571 35.68 235.188, 245964.091 6803955.007 35.677 244.753, 245968.62 6803965.3 35.557 255.999, 245972.611 6803973.783 35.449 265.374, 245978.442 6803988.265 35.305 280.986, 245994.871 6804032.622 35.079 328.287, 246008.168 6804068.165 34.938 366.236, 246012.259 6804081.68 34.954 380.357, 246012.976 6804086.243 34.997 384.976, 246012.389 6804093.934 34.999 392.689, 246010.745 6804110.996 35.054 409.83, 246010.978 6804126.432 34.974 425.268, 246011.31 6804133.395 34.954 432.239, 246013.284 6804140.944 34.94 440.042, 246017.501 6804149.457 34.907 449.542, 246029.545 6804169.466 34.965 472.896, 246068.31 6804228.109 35.129 543.193, 246096.648 6804270.863 35.266 594.486, 246111.36 6804293.968 35.218 621.878, 246120.832 6804306.107 35.119 637.275, 246129.133 6804314.576 35.125 649.134, 246136.586 6804321.578 35.214 659.36, 246148.105 6804329.773 35.214 673.496, 246166.127 6804341.274 35.118 694.875, 246178.685 6804349.243 35.091 709.749, 246194.695 6804359.405 35.072 728.711, 246216.157 6804371.557 35.048 753.375, 246227.6 6804376.79 35.14 765.958, 246239.693 6804381.385 35.204 778.894, 246258.446 6804386.859 35.354 798.43, 246279.937 6804392.733 35.432 820.709, 246291.926 6804394.595 35.45 832.842, 246298.615 6804395.246 35.422 839.562, 246303.637 6804395.539 35.365 844.593, 246313.025 6804394.409 35.279 854.049, 246321.91 6804392.835 35.237 863.072, 246334.124 6804389.124 35.194 875.837, 246355.274 6804380.976 35.336 898.503, 246365.389 6804377.518 35.427 909.192, 246371.825 6804376.069 35.457 915.789)"

        const newLink = new KgvLink("newId:1", newGeom, 124, 3, 271, 915.789, 0, 12141, 1)

        const changes = [
            new ReplaceInfo(null, newLink.id, null, null, 644.607, 915.789),
            new ReplaceInfo(null, newLink.id, null, null, 0, 644.607)
        ];

        const changeSet = new ChangeSet([newLink], changes)
        const expected = [
            {
                "changeType": "add",
                "old": null,
                "new": [
                    {
                        "linkId": "newId:1",
                        "linkLength": 915.789,
                        "geometry": "SRID=3067;LINESTRING ZM(245851.419 6803738.024 35.26 0, 245855.406 6803745.129 34.998 8.147, 245863.125 6803761.792 34.724 26.511, 245874.188 6803783.432 34.857 50.815, 245882.652 6803797.428 34.929 67.171, 245888.252 6803808.127 35.085 79.247, 245893.545 6803821.384 35.131 93.522, 245902.181 6803841.019 35.262 114.972, 245908.998 6803856.221 35.212 131.633, 245920.88 6803878.155 35.283 156.578, 245932.734 6803898.913 35.322 180.483, 245941.213 6803913.429 35.398 197.293, 245952.533 6803933.417 35.579 220.264, 245959.582 6803946.571 35.68 235.188, 245964.091 6803955.007 35.677 244.753, 245968.62 6803965.3 35.557 255.999, 245972.611 6803973.783 35.449 265.374, 245978.442 6803988.265 35.305 280.986, 245994.871 6804032.622 35.079 328.287, 246008.168 6804068.165 34.938 366.236, 246012.259 6804081.68 34.954 380.357, 246012.976 6804086.243 34.997 384.976, 246012.389 6804093.934 34.999 392.689, 246010.745 6804110.996 35.054 409.83, 246010.978 6804126.432 34.974 425.268, 246011.31 6804133.395 34.954 432.239, 246013.284 6804140.944 34.94 440.042, 246017.501 6804149.457 34.907 449.542, 246029.545 6804169.466 34.965 472.896, 246068.31 6804228.109 35.129 543.193, 246096.648 6804270.863 35.266 594.486, 246111.36 6804293.968 35.218 621.878, 246120.832 6804306.107 35.119 637.275, 246129.133 6804314.576 35.125 649.134, 246136.586 6804321.578 35.214 659.36, 246148.105 6804329.773 35.214 673.496, 246166.127 6804341.274 35.118 694.875, 246178.685 6804349.243 35.091 709.749, 246194.695 6804359.405 35.072 728.711, 246216.157 6804371.557 35.048 753.375, 246227.6 6804376.79 35.14 765.958, 246239.693 6804381.385 35.204 778.894, 246258.446 6804386.859 35.354 798.43, 246279.937 6804392.733 35.432 820.709, 246291.926 6804394.595 35.45 832.842, 246298.615 6804395.246 35.422 839.562, 246303.637 6804395.539 35.365 844.593, 246313.025 6804394.409 35.279 854.049, 246321.91 6804392.835 35.237 863.072, 246334.124 6804389.124 35.194 875.837, 246355.274 6804380.976 35.336 898.503, 246365.389 6804377.518 35.427 909.192, 246371.825 6804376.069 35.457 915.789)",
                        "roadClass": 12141,
                        "adminClass": 3,
                        "municipality": 271,
                        "surfaceType": 1,
                        "trafficDirection": 0,
                        "lifeCycleStatus": null
                    }
                ],
                "replaceInfo": [
                    {
                        "oldLinkId": null,
                        "newLinkId": "newId:1",
                        "oldFromMValue": null,
                        "oldToMValue": null,
                        "newFromMValue": 0,
                        "newToMValue": 915.789,
                        "digitizationChange": false
                    }
                ]
            }
        ]
        assert.equal(changeSet.changeEntries[0].changeType, "add");
        assert.equal(changeSet.toJson(), JSON.stringify(expected));
    })

    it("Complete replace info with partial add", () => {
        const oldLinkId = "test:1";
        const newLinkId = "test:2";
        const oldLink = new KgvLink(oldLinkId, testLinkGeom1, 123, 3, 149, 16.93706266, 0, 12141);
        const newLink = new KgvLink(newLinkId, testLinkGeom1, 123, 3, 149, 16.93706266, 0, 12141);
        const change = new ReplaceInfo(oldLinkId, newLinkId, 0, 5.937, 0, 5.937);
        const change2 = new ReplaceInfo(oldLinkId, newLinkId, 5.937, 10.937, 5.937, 10.937);
        const change3 = new ReplaceInfo(undefined, newLinkId, undefined, undefined, 10.937, 16.937);
        const changeSet = new ChangeSet([oldLink, newLink], [change, change2, change3]);
        const expected = [
            {
                "changeType": "replace",
                "old": {
                    "linkId": oldLinkId,
                    "linkLength": 16.937,
                    "geometry": testLinkGeom1,
                    "roadClass": 12141,
                    "adminClass": 3,
                    "municipality": 149,
                    "surfaceType": null,
                    "trafficDirection": 0,
                    "lifeCycleStatus": null
                },
                "new": [
                    {
                        "linkId": newLinkId,
                        "linkLength": 16.937,
                        "geometry": testLinkGeom1,
                        "roadClass": 12141,
                        "adminClass": 3,
                        "municipality": 149,
                        "surfaceType": null,
                        "trafficDirection": 0,
                        "lifeCycleStatus": null
                    }
                ],
                "replaceInfo": [
                    {
                        "oldLinkId": oldLinkId,
                        "newLinkId": newLinkId,
                        "oldFromMValue": 0,
                        "oldToMValue": 16.937,
                        "newFromMValue": 0,
                        "newToMValue": 16.937,
                        "digitizationChange": false
                    }
                ]
            }]
        assert.equal(changeSet.changeEntries[0].changeType, "replace");
        assert.equal(changeSet.toJson(), JSON.stringify(expected));
    })

    it("Complete replace info with partial add on both ends", () => {
        const oldLinkId = "test:1";
        const newLinkId = "test:2";
        const oldLink = new KgvLink(oldLinkId, testLinkGeom1, 123, 3, 149, 16.93706266, 0, 12141);
        const newLink = new KgvLink(newLinkId, testLinkGeom1, 123, 3, 149, 16.93706266, 0, 12141);
        const change = new ReplaceInfo(undefined, newLinkId, undefined, undefined, 0, 5.937);
        const change2 = new ReplaceInfo(oldLinkId, newLinkId, 5.937, 10.937, 5.937, 10.937);
        const change3 = new ReplaceInfo(undefined, newLinkId, undefined, undefined, 10.937, 16.937);
        const changeSet = new ChangeSet([oldLink, newLink], [change, change2, change3]);
        const expected = [
            {
                "changeType": "replace",
                "old": {
                    "linkId": oldLinkId,
                    "linkLength": 16.937,
                    "geometry": testLinkGeom1,
                    "roadClass": 12141,
                    "adminClass": 3,
                    "municipality": 149,
                    "surfaceType": null,
                    "trafficDirection": 0,
                    "lifeCycleStatus": null
                },
                "new": [
                    {
                        "linkId": newLinkId,
                        "linkLength": 16.937,
                        "geometry": testLinkGeom1,
                        "roadClass": 12141,
                        "adminClass": 3,
                        "municipality": 149,
                        "surfaceType": null,
                        "trafficDirection": 0,
                        "lifeCycleStatus": null
                    }
                ],
                "replaceInfo": [
                    {
                        "oldLinkId": oldLinkId,
                        "newLinkId": newLinkId,
                        "oldFromMValue": 0,
                        "oldToMValue": 16.937,
                        "newFromMValue": 0,
                        "newToMValue": 16.937,
                        "digitizationChange": false
                    }
                ]
            }]
        assert.equal(changeSet.changeEntries[0].changeType, "replace");
        assert.equal(changeSet.toJson(), JSON.stringify(expected));
    })

    it("Discard version change if it doesn't conform to link length after partial adds are combined", () => {
        const oldLinkId = "test:1";
        const newLinkId = "test:2";
        const oldLink = new KgvLink(oldLinkId, testLinkGeom1, 123, 3, 149, 16.93706266, 0, 12141);
        const newLink = new KgvLink(newLinkId, testLinkGeom1, 123, 3, 149, 16.93706266, 0, 12141);
        const change = new ReplaceInfo(undefined, newLinkId, undefined, undefined, 0, 5.937);
        const change2 = new ReplaceInfo(oldLinkId, newLinkId, 5.937, 10.937, 5.937, 10.937);
        const change3 = new ReplaceInfo(undefined, newLinkId, undefined, undefined, 10.937, 15.937);
        const changeSet = new ChangeSet([oldLink, newLink], [change, change2, change3]);

        assert.equal(changeSet.toJson(), "[]");
    })

    it("Discard version change if it doesn't form a continuous part with the combined partial adds", () => {
        const oldLinkId = "test:1";
        const newLinkId = "test:2";
        const oldLink = new KgvLink(oldLinkId, testLinkGeom1, 123, 3, 149, 16.93706266, 0, 12141);
        const newLink = new KgvLink(newLinkId, testLinkGeom1, 123, 3, 149, 16.93706266, 0, 12141);
        const change = new ReplaceInfo(undefined, newLinkId, undefined, undefined, 0, 5.937);
        const change2 = new ReplaceInfo(oldLinkId, newLinkId, 5.937, 10.937, 5.937, 10.937);
        const change3 = new ReplaceInfo(undefined, newLinkId, undefined, undefined, 12.937, 16.937);
        const changeSet = new ChangeSet([oldLink, newLink], [change, change2, change3]);

        assert.equal(changeSet.toJson(), "[]");
    })
});