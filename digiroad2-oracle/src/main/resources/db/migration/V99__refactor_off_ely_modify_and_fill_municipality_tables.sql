Alter Table Municipality
Add (ELY_NRO Number, ROAD_MAINTAINER_ID Number);

Alter Table Municipality
Add Constraint Muni_Ely_Mantainer_Unique Unique(ID,ELY_NRO, ROAD_MAINTAINER_ID);

Update Municipality m
Set (m.ELY_NRO, m.ROAD_MAINTAINER_ID) = (Select e.id, Case e.id
	When 1 Then 14
	When 2 Then 12
	When 3 Then 10
	When 4 Then 9
	When 5 Then 8
	When 6 Then 4
	When 7 Then 2
	When 8 Then 3
	When 9 Then 1
	When 0 Then 0
End	as RoadMaintainer
From ELY e Where e.MUNICIPALITY_ID = m.id);

Drop Table Ely Cascade Constraints;