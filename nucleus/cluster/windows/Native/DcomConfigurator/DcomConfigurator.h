#pragma once
using namespace std;

#include "Persona.h"
#include <string>

class DcomConfigurator
{
public:
	DcomConfigurator(int argc, _TCHAR* argv[]);
	~DcomConfigurator();
	int configure();
	inline bool isHelp() { return help; }
	void usage();
protected:
	bool help;
private:
	void printOwners();
	void printOwners(Persona& admin);
	BOOL configureRegKeys();
	void DcomConfigurator::parse(int argc, _TCHAR* argv[]);
	void p(LPCSTR);
	void wp(LPCWSTR);
	bool verbose;
	bool force;

	LPCTSTR scriptingOwnerId;
	LPCTSTR wmiOwnerId;
	LPCTSTR adminOwnerId;
	Persona* admin;
	string message;
};


